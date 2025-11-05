pipeline {
    agent any // Jenkins 마스터 또는 에이전트에서 실행

    // Jenkins Global Tool Configuration에서 설정한 이름
    tools {
        jdk 'corretto-17'
        gradle 'gradle-8.14.3'
    }

    // 환경 변수 정의
    environment {
        // --- 서비스별 수정 필요 --- #❗서비스별로 SERVICE_NAME, ECS_CONTAINER_NAME만 수정하면 됩니다. e.g: 'member-service', 'member'
        SERVICE_NAME                = 'member-service'
        ECS_CONTAINER_NAME          = 'member'
        SONAR_PROJECT_KEY           = "couponpop-${SERVICE_NAME}"

        // --- 공통 (Jenkins EC2 IAM 역할이 권한을 가짐) ---
        AWS_REGION                  = 'ap-northeast-2'
        ECR_REPO_NAME               = "couponpop/${SERVICE_NAME}"
        ECS_CLUSTER_NAME            = 'couponpop-ecs-cluster'
        ECS_SERVICE_NAME            = "${SERVICE_NAME}" // ECS 서비스 이름 확인
        ECS_TASK_DEFINITION_FAMILY  = "couponpop-${SERVICE_NAME}-task-definition" // Task Def Family 확인
        SONAR_HOST_URL              = 'http://sonarqube:9000' // Jenkins 시스템 설정과 일치

        // --- Jenkins Credentials ID ---
        AWS_ACCOUNT_ID_CREDENTIALS_ID = 'aws-account-id'
        GPR_CREDENTIALS_ID          = 'github-packages-token' // GitHub Packages 읽기용 PAT
        FCM_KEY_CREDENTIALS_ID      = 'fcm-service-account-key' // FCM 키 파일
        SONAR_TOKEN_CREDENTIALS_ID  = 'sonarqube-token' // SonarQube 토큰
    }

    stages {

        // === 'CI' 상위 스테이지 ===
        stage('CI') {
            when {
                allOf {
                    // 조건 1: dev, main, PR 그리고 '테스트 브랜치'일 때
                    anyOf {
                        branch 'main'
                        branch 'dev'
                        branch 'chore/jenkins-test' // [테스트 브랜치 추가]
                        changeRequest() // PR
                    }
                    // 조건 2: 빌드 필요 파일이 변경되었을 때
                    anyOf {
                        changeset pattern: 'src/**', comparator: 'GLOB'
                        changeset pattern: 'build.gradle', comparator: 'GLOB'
                        changeset pattern: 'settings.gradle', comparator: 'GLOB'
                        changeset pattern: 'gradlew', comparator: 'GLOB'
                        changeset pattern: 'gradle/**', comparator: 'GLOB'
                        changeset pattern: 'Jenkinsfile', comparator: 'GLOB'
                        changeset pattern: 'Dockerfile', comparator: 'GLOB'
                    }
                }
            }
            stages {

                // === 1. Checkout ===
                stage('Checkout') {
                    steps {
                        script {
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
                        withCredentials([file(credentialsId: env.FCM_KEY_CREDENTIALS_ID, variable: 'FCM_KEY_FILE')]) {
                            sh 'mkdir -p src/main/resources/firebase'
                            sh 'cp $FCM_KEY_FILE src/main/resources/firebase/serviceAccountKey.json'
                        }
                    }
                }

                // === 3. Build, Test & Generate Reports ===
                stage('Build, Test & Generate Reports') {
                    steps {
                        withCredentials([usernamePassword(credentialsId: env.GPR_CREDENTIALS_ID, usernameVariable: 'GITHUB_ACTOR', passwordVariable: 'GITHUB_TOKEN')]) {
                            sh 'chmod +x ./gradlew'
                            sh '''
                            SPRING_PROFILES_ACTIVE=test \
                            TZ=Asia/Seoul \
                            ./gradlew clean build --no-daemon || exit 1
                            rm -f build/libs/*plain*.jar
                            '''
                        }
                    }
                }

                // === 4. SonarQube Analysis ===
                stage('SonarQube Analysis') {
                    steps {
                        withSonarQubeEnv('SonarQube') {
                            withCredentials([string(credentialsId: env.SONAR_TOKEN_CREDENTIALS_ID, variable: 'SONAR_TOKEN')]) {
                                sh '''
                                ./gradlew sonar \
                                -Dsonar.projectKey=${SONAR_PROJECT_KEY} \
                                -Dsonar.projectName=${SONAR_PROJECT_KEY} \
                                -Dsonar.login=${SONAR_TOKEN} \
                                -Dsonar.host.url=${SONAR_HOST_URL} \
                                -Dsonar.coverage.jacoco.xmlReportPaths=build/reports/jacoco/test/jacocoTestReport.xml
                                '''
                                // GHA와 달리 Jenkins는 localhost에서 Redis, Elasticsearch를 자동 실행하지 않습니다.
                                // 이 테스트가 성공하려면 Jenkins 실행 환경에 Redis/Elasticsearch가 있거나,
                                // Testcontainers를 사용하도록 build.gradle이 설정되어야 합니다.
                            }
                        }
                        timeout(time: 5, unit: 'MINUTES') {
                            waitForQualityGate abortPipeline: true
                        }
                    }
                }

            } // 'CI' 하위 stages 끝
        } // 'CI' 상위 stage 끝


        // === 'Deploy' 상위 스테이지 ===
        stage('Deploy to Production') {
            when {
                allOf {
                    // 조건 1: 'main' 브랜치 또는 '테스트 브랜치'일 때
                    anyOf {
                        branch 'main'
                        branch 'chore/jenkins-test' // [테스트 브랜치 추가]
                    }
                    // 조건 2: 빌드 필요 파일이 변경되었을 때
                    anyOf {
                        changeset pattern: 'src/**', comparator: 'GLOB'
                        changeset pattern: 'build.gradle', comparator: 'GLOB'
                        changeset pattern: 'settings.gradle', comparator: 'GLOB'
                        changeset pattern: 'gradlew', comparator: 'GLOB'
                        changeset pattern: 'gradle/**', comparator: 'GLOB'
                        changeset pattern: 'Jenkinsfile', comparator: 'GLOB'
                        changeset pattern: 'Dockerfile', comparator: 'GLOB'
                    }
                }
            }
            stages {

                // === 5. Build & Push Docker Image ===
                stage('Build & Push Docker Image') {
                    steps {
                        withCredentials([string(credentialsId: env.AWS_ACCOUNT_ID_CREDENTIALS_ID, variable: 'AWS_ACCOUNT_ID')]) {
                            script {
                                def ECR_REGISTRY = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
                                def imageTag = "${ECR_REGISTRY}/${ECR_REPO_NAME}:${env.BUILD_NUMBER}"  // 빌드 번호로 태그
                                def latestTag = "${ECR_REGISTRY}/${ECR_REPO_NAME}:latest"

                                sh 'aws ecr get-login-password --region ' + AWS_REGION + ' | docker login --username AWS --password-stdin ' + ECR_REGISTRY
                                sh 'docker build -t ' + imageTag + ' -t ' + latestTag + ' .'
                                sh 'docker push ' + imageTag
                                sh 'docker push ' + latestTag
                            }
                        }
                    }
                }

                // === 6. Deploy to ECS ===
                stage('Deploy to ECS') {
                    steps {
                        withCredentials([string(credentialsId: env.AWS_ACCOUNT_ID_CREDENTIALS_ID, variable: 'AWS_ACCOUNT_ID')]) {
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
                                echo 'New Image URI to set: ' + currentImageUri

                                def containerToUpdate = containerDefinitions.find { it.name == env.ECS_CONTAINER_NAME }
                                if (!containerToUpdate) {
                                    error "Container with name '${env.ECS_CONTAINER_NAME}' not found in task definition '${ECS_TASK_DEFINITION_FAMILY}'."
                                }
                                containerToUpdate.image = currentImageUri.toString()

                                // 3. 새 Task Definition 등록에 필요한 페이로드 생성
                                def newTaskDefinitionPayload = taskDefJson.taskDefinition

                                newTaskDefinitionPayload.remove('taskDefinitionArn')
                                newTaskDefinitionPayload.remove('revision')
                                newTaskDefinitionPayload.remove('status')
                                newTaskDefinitionPayload.remove('requiresAttributes')
                                newTaskDefinitionPayload.remove('compatibilities')
                                newTaskDefinitionPayload.remove('registeredAt')
                                newTaskDefinitionPayload.remove('registeredBy')

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

            } // 'Deploy' 하위 stages 끝
        } // 'Deploy' 상위 stage 끝

    } // stages 끝

    // 빌드 후 항상 실행
    post {
        // 'success' 블록: 빌드가 성공했을 때만 리포트/아티팩트를 수집
        success {
            archiveArtifacts artifacts: 'build/reports/jacoco/test/html/**', allowEmptyArchive: true, fingerprint: true
            archiveArtifacts artifacts: 'build/reports/tests/test/**', allowEmptyArchive: true, fingerprint: true
            junit allowEmptyResults: true, testResults: 'build/test-results/test/*.xml'
        }
        // 'always' 블록: 스테이지 실행 여부와 관계없이 항상 정리
        always {
            sh 'rm -f src/main/resources/firebase/serviceAccountKey.json'
            cleanWs() // 워크스페이스 정리
        }
    }
} // pipeline 끝