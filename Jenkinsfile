pipeline {
    agent any // Jenkins 마스터 또는 에이전트에서 실행

    // Jenkins Global Tool Configuration에서 설정한 이름
    tools {
        jdk 'corretto-17'
        gradle 'gradle-8.14.3'
    }

    // 환경 변수 정의
    environment {
        // --- 서비스별 수정 필요 --- #❗서비스별로 SERVICE_NAME만 수정하면 됩니다. e.g: 'member-service', coupon-service'
        SERVICE_NAME                = 'member-service'
        SONAR_PROJECT_KEY           = "couponpop-${SERVICE_NAME}"

        // --- 공통 (Jenkins EC2 IAM 역할이 권한을 가짐) ---
        AWS_REGION                  = 'ap-northeast-2'
        ECR_REPO_NAME               = "couponpop/${SERVICE_NAME}"
        ECS_CLUSTER_NAME            = 'couponpop-ecs-cluster'
        ECS_SERVICE_NAME            = "${SERVICE_NAME}" // ECS 서비스 이름 확인
        ECS_TASK_DEFINITION_FAMILY  = "couponpop-${SERVICE_NAME}-task-definition" // Task Def Family 확인
        SONAR_HOST_URL              = 'http://sonarqube:9000' // Jenkins 시스템 설정과 일치

        // --- Jenkins Credentials ID ---
        AWS_ACCOUNT_ID_CREDENTIAL_ID = 'aws-account-id'
        GPR_CREDENTIALS_ID          = 'github-packages-token' // GitHub Packages 읽기용 PAT
        FCM_KEY_CREDENTIALS_ID      = 'fcm-service-account-key' // FCM 키 파일
        SONAR_TOKEN_CREDENTIALS_ID  = 'sonarqube-token' // SonarQube 토큰
    }

    when {
        not {
            anyOf {
                changeset pattern: '**/*.md', comparator: 'GLOB'
                changeset pattern: 'docs/**', comparator: 'GLOB'
                changeset pattern: '.github/**', comparator: 'GLOB'
                changeset pattern: '.gitignore', comparator: 'GLOB'
                changeset pattern: 'LICENSE', comparator: 'GLOB'
            }
        }
    }

    stages {
        // === 1. Checkout ===
        stage('Checkout') {
            // dev, main, PR일 때만 실행
//             when {
//                 anyOf {
//                     branch 'main'
//                     branch 'dev'
//                     changeRequest() // PR
//                 }
//             }
            steps {
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
            // dev, main, PR일 때만 실행
            when {
                anyOf {
                    branch 'main'
                    branch 'dev'
                    changeRequest() // PR
                }
            }
            steps {
                withCredentials([file(credentialsId: FCM_KEY_CREDENTIALS_ID, variable: 'FCM_KEY_FILE')]) {
                    sh 'mkdir -p src/main/resources/firebase'
                    sh 'cp $FCM_KEY_FILE src/main/resources/firebase/serviceAccountKey.json'
                }
            }
        }

        // === 3. Build, Test & Generate Reports ===
        stage('Build, Test & Generate Reports') {
            // dev, main, PR일 때만 실행
//             when {
//                 anyOf {
//                     branch 'main'
//                     branch 'dev'
//                     changeRequest() // PR
//                 }
//             }
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

        // === 4. SonarQube Analysis ===
        stage('SonarQube Analysis') {
            // dev, main, PR일 때만 실행
//             when {
//                 anyOf {
//                     branch 'main'
//                     branch 'dev'
//                     changeRequest() // PR
//                 }
//             }
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

        // === 5. Build & Push Docker Image ===
        stage('Build & Push Docker Image') {
            // 'main' 브랜치일 때만 실행
//             when {
//                 branch 'main'
//             }
            steps {
                withCredentials([string(credentialsId: AWS_ACCOUNT_ID_CREDENTIAL_ID, variable: 'AWS_ACCOUNT_ID')]) {
                    script {
                        def ECR_REGISTRY = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

                        def imageTag = "${ECR_REGISTRY}/${ECR_REPO_NAME}:${env.BUILD_NUMBER}"  // 빌드 번호로 태그
                        def latestTag = "${ECR_REGISTRY}/${ECR_REPO_NAME}:latest"

                        sh "aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${ECR_REGISTRY}"

                        // Dockerfile이 GPR에 접근하지 않으므로 withCredentials 및 build-arg 사용하지 않음
                        sh "docker build -t ${imageTag} -t ${latestTag} ."

                        sh "docker push ${imageTag}"
                        sh "docker push ${latestTag}"
                    }
                }
            }
        }

        // === 6. Deploy to ECS ===
        stage('Deploy to ECS') {
            // 'main' 브랜치일 때만 실행
//             when {
//                 branch 'main'
//             }
            steps {
                withCredentials([string(credentialsId: AWS_ACCOUNT_ID_CREDENTIAL_ID, variable: 'AWS_ACCOUNT_ID')]) {
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

                        // 빌드된 이미지 URI (예: 12345612345.dkr.ecr.ap-northeast-2.amazonaws.com/couponpop/member-service:17)
                        def ECR_REGISTRY = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
                        def currentImageUri = "${ECR_REGISTRY}/${ECR_REPO_NAME}:${env.BUILD_NUMBER}"
                        echo "New Image URI to set: ${currentImageUri}"

                        // [SERVICE_NAME과 일치하는 컨테이너를 동적으로 찾기
                        def containerToUpdate = containerDefinitions.find { it.name == env.SERVICE_NAME }
                        if (!containerToUpdate) {
                            error "Container with name '${env.SERVICE_NAME}' not found in task definition '${ECS_TASK_DEFINITION_FAMILY}'."
                        }
                        containerToUpdate.image = currentImageUri.toString()

                        // 3. 새 Task Definition 등록에 필요한 다른 속성들 추출 및 정리
                        def newTaskDefinitionPayload = taskDefJson.taskDefinition

                        // register-task-definition API에서 허용하지 않는 필드만 제거
                        newTaskDefinitionPayload.remove('taskDefinitionArn')
                        newTaskDefinitionPayload.remove('revision')
                        newTaskDefinitionPayload.remove('status')
                        newTaskDefinitionPayload.remove('requiresAttributes')
                        newTaskDefinitionPayload.remove('compatibilities')
                        newTaskDefinitionPayload.remove('registeredAt')    // (메타데이터 제거)
                        newTaskDefinitionPayload.remove('registeredBy')   // (메TA데이터 제거)

                        // describe-task-definition 결과의 최상위 'tags'를 payload에 추가
                        if (taskDefJson.tags) {
                            newTaskDefinitionPayload.tags = taskDefJson.tags
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