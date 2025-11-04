pipeline {
    agent any // Jenkins 마스터 또는 에이전트에서 실행

    // Jenkins Global Tool Configuration에서 설정한 이름
    tools {
        jdk 'corretto-17'
        gradle 'gradle-8.14.3'
    }

    // 환경 변수 정의
    environment {
        // --- 서비스별 수정 필요 ---
        SERVICE_NAME                = 'member-service' // 예: 'api-gateway', 'coupon-service'
        SONAR_PROJECT_KEY           = "couponpop-${SERVICE_NAME}"

        // --- 공통 (Jenkins EC2 IAM 역할이 권한을 가짐) ---
        AWS_REGION                  = 'ap-northeast-2'
        AWS_ACCOUNT_ID              = '802318301972' // 본인 AWS 계정 ID로 변경
        ECR_REGISTRY                = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
        ECR_REPO_NAME               = "couponpop/${SERVICE_NAME}"
        ECS_CLUSTER_NAME            = 'couponpop-ecs-cluster'
        ECS_SERVICE_NAME            = "${SERVICE_NAME}" // ECS 서비스 이름 확인
        ECS_TASK_DEFINITION_FAMILY  = "couponpop-${SERVICE_NAME}-task-definition" // Task Def Family 확인
        SONAR_HOST_URL              = 'http://sonarqube:9000' // Jenkins 시스템 설정과 일치

        // --- Jenkins Credentials ID ---
        GPR_CREDENTIALS_ID          = 'github-packages-token' // GitHub Packages 읽기용 PAT
        FCM_KEY_CREDENTIALS_ID      = 'fcm-service-account-key' // FCM 키 파일
        SONAR_TOKEN_CREDENTIALS_ID  = 'sonarqube-token' // SonarQube 토큰
    }

    stages {
        // === 1. Checkout ===
        stage('Checkout') {
            steps {
                // Multibranch Pipeline이 자동으로 코드를 checkout 해줍니다.
                script {
                    // PULL_REQUEST인 경우 PR 관련 변수 설정 (SonarQube 분석용)
                    if (env.CHANGE_ID) {
                        env.PR_ID = env.CHANGE_ID
                        env.PR_BRANCH = env.CHANGE_BRANCH
                        env.PR_TARGET = env.CHANGE_TARGET
                    }
                }
            }
        }

        // === 2. Prepare Test Env ===
        stage('Prepare Test Env') {
            steps {
                withCredentials([file(credentialsId: FCM_KEY_CREDENTIALS_ID, variable: 'FCM_KEY_FILE')]) {
                    sh 'mkdir -p src/main/resources/firebase'
                    sh 'cp $FCM_KEY_FILE src/main/resources/firebase/serviceAccountKey.json'
                }
            }
        }

        // === 3. Build, Test & Generate Reports (모든 브랜치) ===
        stage('Build, Test & Generate Reports') {
            steps {
                withCredentials([usernamePassword(credentialsId: GPR_CREDENTIALS_ID, usernameVariable: 'GITHUB_ACTOR', passwordVariable: 'GITHUB_TOKEN')]) {
                    sh 'chmod +x ./gradlew'
                    sh '''
                    SPRING_PROFILES_ACTIVE=test \
                    TZ=Asia/Seoul \
                    ./gradlew clean build --no-daemon || exit 1
                    rm -f build/libs/*plain*.jar
                    '''
                    // GHA와 달리 Jenkins는 localhost에서 Redis, Elasticsearch를 자동 실행하지 않습니다.
                    // 이 테스트가 성공하려면 Jenkins 실행 환경에 Redis/Elasticsearch가 있거나,
                    // Testcontainers를 사용하도록 build.gradle이 설정되어야 합니다.
                }
            }
        }

        // === 4. SonarQube Analysis (모든 브랜치) ===
        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv('SonarQube') {
                    withCredentials([string(credentialsId: SONAR_TOKEN_CREDENTIALS_ID, variable: 'SONAR_TOKEN')]) {
                        sh '''
                        ./gradlew sonar \
                        -Dsonar.projectKey=${SONAR_PROJECT_KEY} \
                        -Dsonar.projectName=${SONAR_PROJECT_KEY} \
                        -Dsonar.login=${SONAR_TOKEN} \
                        -Dsonar.host.url=${SONAR_HOST_URL} \
                        -Dsonar.coverage.jacoco.xmlReportPaths=build/reports/jacoco/test/jacocoTestReport.xml
                        '''
                    }
                }
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        // === 5. Build & Push Docker Image (main 브랜치 푸시 시에만) ===
        stage('Build & Push Docker Image') {
            when {
                // branch 'main' // (테스트 완료 후 'main'으로 변경)
                branch 'chore/apply-jenkins'
            }
            steps {
                script {
                    def imageTag = "${ECR_REGISTRY}/${ECR_REPO_NAME}:${env.BUILD_NUMBER}" // 빌드 번호로 태그
                    def latestTag = "${ECR_REGISTRY}/${ECR_REPO_NAME}:latest"

                    sh "aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${ECR_REGISTRY}"

                    // Dockerfile이 GPR에 접근하지 않으므로 withCredentials 및 build-arg 제거
                    sh "docker build -t ${imageTag} -t ${latestTag} ."

                    sh "docker push ${imageTag}"
                    sh "docker push ${latestTag}"
                }
            }
        }

        // === 6. Deploy to ECS (main 브랜치 푸시 시에만) ===
        stage('Deploy to ECS') {
            when {
                // branch 'main' // (테스트 완료 후 'main'으로 변경)
                branch 'chore/apply-jenkins'
            }
            steps {
                script {
                    // 1. 현재 Task Definition 가져오기
                    echo "ECS_TASK_DEFINITION_FAMILY: ${ECS_TASK_DEFINITION_FAMILY}"
                    def currentTaskDef = sh(
                        returnStdout: true,
                        script: "aws ecs describe-task-definition --task-definition ${ECS_TASK_DEFINITION_FAMILY} --region ${AWS_REGION}"
                    ).trim()

                    // 2. 컨테이너 이미지 정의를 새 이미지 태그로 변경
                    def taskDefJson = readJSON(text: currentTaskDef)
                    def containerDefinitions = taskDefJson.taskDefinition.containerDefinitions

                    if (containerDefinitions == null || containerDefinitions.isEmpty()) {
                        error "Task Definition ${ECS_TASK_DEFINITION_FAMILY} has no containerDefinitions."
                    }

                    // 빌드된 이미지 URI (예: 802318301972.dkr.ecr.ap-northeast-2.amazonaws.com/couponpop/member-service:17)
                    def currentImageUri = "${ECR_REGISTRY}/${ECR_REPO_NAME}:${env.BUILD_NUMBER}"
                    echo "New Image URI to set: ${currentImageUri}"

                    containerDefinitions[0].image = currentImageUri.toString()

                    // 3. 새 Task Definition 등록에 필요한 다른 속성들 추출 및 정리
                    def newTaskDefinitionPayload = [:]
                    newTaskDefinitionPayload.family = taskDefJson.taskDefinition.family
                    newTaskDefinitionPayload.containerDefinitions = containerDefinitions
                    newTaskDefinitionPayload.networkMode = taskDefJson.taskDefinition.networkMode
                    newTaskDefinitionPayload.cpu = taskDefJson.taskDefinition.cpu
                    newTaskDefinitionPayload.memory = taskDefJson.taskDefinition.memory
                    newTaskDefinitionPayload.requiresCompatibilities = taskDefJson.taskDefinition.requiresCompatibilities

                    if (taskDefJson.taskDefinition.taskRoleArn) {
                        newTaskDefinitionPayload.taskRoleArn = taskDefJson.taskDefinition.taskRoleArn
                    }
                    if (taskDefJson.taskDefinition.executionRoleArn) {
                        newTaskDefinitionPayload.executionRoleArn = taskDefJson.taskDefinition.executionRoleArn
                    }
                    if (taskDefJson.taskDefinition.volumes) {
                        newTaskDefinitionPayload.volumes = taskDefJson.taskDefinition.volumes
                    }
                    if (taskDefJson.taskDefinition.tags) {
                        newTaskDefinitionPayload.tags = taskDefJson.taskDefinition.tags
                    }

                    def taskDefFilePath = "new-task-definition.json"
                    writeJSON(file: taskDefFilePath, json: newTaskDefinitionPayload, pretty: 1)
                    echo "New Task Definition Payload written to ${taskDefFilePath}"

                    def newTaskDef = sh(
                        returnStdout: true,
                        script: """
                            aws ecs register-task-definition \
                            --cli-input-json "file://${taskDefFilePath}" \
                            --region ${AWS_REGION}
                        """
                    ).trim()

                    def newTaskDefArn = readJSON(text: newTaskDef).taskDefinition.taskDefinitionArn
                    echo "Registered new Task Definition: ${newTaskDefArn}"

                    // 4. 새 Task Definition 이용하여 업데이트
                    sh """
                    aws ecs update-service \
                      --cluster ${ECS_CLUSTER_NAME} \
                      --service ${ECS_SERVICE_NAME} \
                      --task-definition ${newTaskDefArn} \
                      --region ${AWS_REGION}
                    """

                    sh """
                    echo "Waiting for service ${ECS_SERVICE_NAME} to stabilize..."
                    aws ecs wait services-stable \
                      --cluster ${ECS_CLUSTER_NAME} \
                      --service ${ECS_SERVICE_NAME} \
                      --region ${AWS_REGION}
                    """
                }
            }
        }
    } // stages 끝

    // 빌드 후 항상 실행
    post {
        always {
            sh 'rm -f src/main/resources/firebase/serviceAccountKey.json'
            archiveArtifacts artifacts: 'build/reports/jacoco/test/html/**', allowEmptyArchive: true, fingerprint: true
            archiveArtifacts artifacts: 'build/reports/tests/test/**', allowEmptyArchive: true, fingerprint: true
            junit 'build/test-results/test/*.xml'
            cleanWs() // 워크스페이스 정리
        }
    }
} // pipeline 끝