pipeline {
    agent any // Jenkins 마스터 또는 에이전트에서 실행

    // Jenkins Global Tool Configuration에서 설정한 이름
    tools {
        jdk 'corretto-17'
        gradle 'gradle-8.14.3'
    }

    // 환경 변수 정의
    environment {
        // --- 서비스별 수정 필요 --- #❗서비스별로 SERVICE_NAME, ECS_CONTAINER_NAME만 수정하면 됩니다.
        SERVICE_NAME                = 'member-service'
        ECS_CONTAINER_NAME          = 'member'
        SONAR_PROJECT_KEY           = "couponpop-${SERVICE_NAME}"

        // --- AWS 변수 (B/G 스크립트에서 사용) ---
        AWS_REGION                  = 'ap-northeast-2'
        ECR_REPO_NAME               = "couponpop/${SERVICE_NAME}"
        ECS_CLUSTER_NAME            = 'couponpop-ecs-cluster'
        ECS_SERVICE_NAME            = "${SERVICE_NAME}"
        ECS_TASK_DEFINITION_FAMILY  = "couponpop-${SERVICE_NAME}-task-definition"

        // ECR 전체 URI (빌드 후 ECR 레지스트리 경로)
        // [수정됨]: AWS_ACCOUNT_ID 변수를 사용하여 ECR 전체 URI를 동적으로 구성할 준비를 합니다.
        ECR_REGISTRY_URI_PREFIX     = '' // <--- Step 5에서 AWS_ACCOUNT_ID로 채워짐

        // --- Jenkins Credentials ID ---
        AWS_ACCOUNT_ID_CREDENTIALS_ID = 'aws-account-id'
        GPR_CREDENTIALS_ID          = 'github-packages-token'
        FCM_KEY_CREDENTIALS_ID      = 'fcm-service-account-key'
        SONAR_TOKEN_CREDENTIALS_ID  = 'sonarqube-token'

        SONAR_HOST_URL              = 'http://sonarqube:9000'
    }

    stages {

        // === 'CI' 상위 스테이지 ===
        stage('CI') {
            when {
                // [수정됨]: changeset 조건을 제거하여 트리거 민감도를 낮춤 (Jenkins가 작동하는지 확인용)
                anyOf {
                    branch 'main'
                    branch 'dev'
                    branch 'feat/apply-new-jenkins'
                    changeRequest()
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
             anyOf {
                 branch 'main'
                 branch 'feat/apply-new-jenkins'
             }
         }

         // [수정됨] 상위 steps 블록 제거

         stages {

             // === 5. Build & Push Docker Image ===
            stage('Build & Push Docker Image') {
                steps {
                    withCredentials([string(credentialsId: env.AWS_ACCOUNT_ID_CREDENTIALS_ID, variable: 'AWS_ACCOUNT_ID')]) {
                        script {
                            // [추가] 변수 값 자체를 디버깅
                            if (AWS_ACCOUNT_ID == null || AWS_ACCOUNT_ID.isEmpty()) {
                                error "FATAL: 'aws-account-id' credential secret is empty or null!"
                            }

                            // 안전하게 재할당
                            env.ECR_REGISTRY_URI_PREFIX = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
                            echo "ECR Registry: ${env.ECR_REGISTRY_URI_PREFIX}"
                             def imageTag = "${env.ECR_REGISTRY_URI_PREFIX}/${env.ECR_REPO_NAME}:${env.BUILD_NUMBER}"
                             def latestTag = "${env.ECR_REGISTRY_URI_PREFIX}/${env.ECR_REPO_NAME}:latest"

                             sh 'aws ecr get-login-password --region ' + AWS_REGION + ' | docker login --username AWS --password-stdin ' + env.ECR_REGISTRY_URI_PREFIX
                             sh 'docker build -t ' + imageTag + ' -t ' + latestTag + ' .'
                             sh 'docker push ' + imageTag
                             sh 'docker push ' + latestTag
                         }
                     }
                 }
             }

             // === 6. Deploy to ECS (Blue/Green 반영) ===
            stage('Deploy to ECS') {
                 steps {
                     // [수정됨] withCredentials 블록을 하위 스테이지로 다시 이동
                     withCredentials([string(credentialsId: env.AWS_ACCOUNT_ID_CREDENTIALS_ID, variable: 'AWS_ACCOUNT_ID')]) {
                         script {
                             // 5단계에서 설정한 env 변수를 사용하거나,
                             // 만약 5단계가 스킵될 경우를 대비해 여기서도 안전하게 재할당
                             if (env.ECR_REGISTRY_URI_PREFIX == null || env.ECR_REGISTRY_URI_PREFIX.isEmpty()) {
                                 env.ECR_REGISTRY_URI_PREFIX = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
                             }

                             sh """
                             # sh 블록 시작 시 set -e (오류 발생 시 즉시 중단)를 설정합니다.
                             set -e

                             # ===========================================
                             # Blue/Green Deployment Script (AWS 가이드 기반)
                             # ===========================================
                             # 환경 변수 설정 (Jenkins ENV 사용)
                             CLUSTER_NAME="${ECS_CLUSTER_NAME}"
                             SERVICE_NAME="${ECS_SERVICE_NAME}"
                             TASK_DEFINITION_FAMILY="${ECS_TASK_DEFINITION_FAMILY}"
                             # ECR_REGISTRY_URI_PREFIX 변수가 이제 정상적으로 설정됨
                             IMAGE_URI="${ECR_REGISTRY_URI_PREFIX}/${ECR_REPO_NAME}:${BUILD_NUMBER}"
                             REGION="${AWS_REGION}"

                             echo "=========================================="
                             echo "Starting Blue/Green Deployment"
                             echo "Cluster: \${CLUSTER_NAME}"
                             echo "Service: \${SERVICE_NAME}"
                             echo "New Image: \${IMAGE_URI}"
                             echo "=========================================="

                             # 1. 현재 태스크 정의 가져오기
                             echo "📋 Retrieving current task definition..."
                             CURRENT_TASK_DEF=\$(aws ecs describe-task-definition \
                                 --task-definition \${TASK_DEFINITION_FAMILY} \
                                 --region \${REGION} \
                                 --query 'taskDefinition')

                             # 2. 새 태스크 정의 생성 및 업데이트
                             echo "🔄 Creating new task definition..."
                             NEW_TASK_DEF=\$(echo \${CURRENT_TASK_DEF} | jq --arg IMAGE "\${IMAGE_URI}" '
                                 .containerDefinitions[0].image = \$IMAGE |
                                 del(.taskDefinitionArn, .revision, .status, .requiresAttributes, .placementConstraints, .compatibilities, .registeredAt, .registeredBy)')

                             # 3. 새 태스크 정의 등록
                             echo "📝 Registering new task definition..."
                             NEW_TASK_DEF_ARN=\$(aws ecs register-task-definition \
                                 --region \${REGION} \
                                 --cli-input-json "\${NEW_TASK_DEF}" \
                                 --query 'taskDefinition.taskDefinitionArn' \
                                 --output text)
                             echo "✅ New task definition: \${NEW_TASK_DEF_ARN}"

                             # 4. 블루/그린 배포 시작
                             echo "🚀 Starting blue/green deployment..."
                             # deploymentCircuitBreaker를 비활성화하고 bakeTimeInMinutes를 5분으로 설정
                             aws ecs update-service \
                                 --cluster \${CLUSTER_NAME} \
                                 --service \${SERVICE_NAME} \
                                 --task-definition \${NEW_TASK_DEF_ARN} \
                                 --force-new-deployment \
                                 --deployment-configuration '{
                                     "deploymentCircuitBreaker": {
                                         "enable": false
                                     },
                                     "blueGreenDeployment": {
                                         "strategy": "BLUE_GREEN",
                                         "bakeTimeInMinutes": 5
                                     }
                                 }' \
                                 --region \${REGION} > /dev/null

                             # 5. 배포 모니터링 및 완료 대기
                             echo "👀 Monitoring deployment (max 30 minutes)..."
                             TIMEOUT=1800  # 30분 타임아웃 (AWS 가이드 유지)
                             ELAPSED=0
                             while [ \${ELAPSED} -lt \${TIMEOUT} ]; do
                                 SERVICE_INFO=\$(aws ecs describe-services \
                                     --cluster \${CLUSTER_NAME} \
                                     --services \${SERVICE_NAME} \
                                     --region \${REGION} \
                                     --query 'services[0]')

                                 DEPLOYMENT_STATUS=\$(echo \${SERVICE_INFO} | jq -r '.deployments[0].status')
                                 RUNNING_COUNT=\$(echo \${SERVICE_INFO} | jq -r '.runningCount')
                                 DESIRED_COUNT=\$(echo \${SERVICE_INFO} | jq -r '.desiredCount')

                                 echo "Status: \${DEPLOYMENT_STATUS} | Running: \${RUNNING_COUNT}/\${DESIRED_COUNT}"

                                 if [ "\${DEPLOYMENT_STATUS}" = "PRIMARY" ] && [ "\${RUNNING_COUNT}" = "\${DESIRED_COUNT}" ]; then
                                     echo "🎉 Blue/Green deployment completed successfully!"
                                     exit 0
                                 elif [ "\${DEPLOYMENT_STATUS}" = "FAILED" ]; then
                                     echo "💥 Deployment failed!"
                                     exit 1
                                 fi

                                 sleep 30
                                 ELAPSED=\$((ELAPSED + 30))
                             done
                             echo "⏰ Deployment timeout reached!"
                             exit 1
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