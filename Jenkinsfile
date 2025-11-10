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

        // --- Jenkins Credentials ID ---
        AWS_ACCOUNT_ID_CREDENTIALS_ID = 'aws-account-id'
        GPR_CREDENTIALS_ID          = 'github-packages-token'
        FCM_KEY_CREDENTIALS_ID      = 'fcm-service-account-key'
        SONAR_TOKEN_CREDENTIALS_ID  = 'sonarqube-token'

        REDIS_HOST_CREDENTIAL       = 'redis-host-for-test'
        REDIS_PORT_CREDENTIAL       = 'redis-port-for-test'
        RABBITMQ_HOST_CREDENTIAL    = 'rabbitmq-host-for-test'
        RABBITMQ_PORT_CREDENTIAL    = 'rabbitmq-port-for-test'
        JWT_SECRET_KEY_CREDENTIAL   = 'jwt-secret-key-for-test'
        DB_URL_KEY_CREDENTIAL       = 'db-url-for-test'

        SONAR_HOST_URL              = 'http://sonarqube:9000'
    }

    stages {

        // === 'CI' 상위 스테이지 ===
        stage('CI') {
        when {
                // main/dev 푸시 또는 main/dev로의 PR일 때 (파일 필터링 없음)
                anyOf {
                    branch 'main'
                    branch 'dev'
                    changeRequest(target: 'main')
                    changeRequest(target: 'dev')
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
                          withCredentials([
                              usernamePassword(credentialsId: env.GPR_CREDENTIALS_ID, usernameVariable: 'GITHUB_ACTOR', passwordVariable: 'GITHUB_TOKEN'),
                              string(credentialsId: env.REDIS_HOST_CREDENTIAL, variable: 'REDIS_HOST'),
                              string(credentialsId: env.REDIS_PORT_CREDENTIAL, variable: 'REDIS_PORT'),
                              string(credentialsId: env.RABBITMQ_HOST_CREDENTIAL, variable: 'RABBITMQ_HOST'),
                              string(credentialsId: env.RABBITMQ_PORT_CREDENTIAL, variable: 'RABBITMQ_PORT'),
                              string(credentialsId: env.JWT_SECRET_KEY_CREDENTIAL, variable: 'JWT_SECRET_KEY'),
                              string(credentialsId: env.DB_URL_KEY_CREDENTIAL, variable: 'DB_URL')
                          ]) {
                              sh 'chmod +x ./gradlew'
                              sh '''
                                  set -e

                                  # Application Context 로딩에 필요한 모든 환경 변수 주입
                                  # 이 변수들은 Shell 환경에서만 사용 (System.getenv()가 읽음)
                                  SPRING_PROFILES_ACTIVE=test \
                                  TZ=Asia/Seoul \
                                  REDIS_HOST=${REDIS_HOST} \
                                  REDIS_PORT=${REDIS_PORT} \
                                  RABBITMQ_HOST=${RABBITMQ_HOST} \
                                  RABBITMQ_PORT=${RABBITMQ_PORT} \
                                  JWT_SECRET_KEY=${JWT_SECRET_KEY} \
                                  GITHUB_ACTOR=${GITHUB_ACTOR} \
                                  GITHUB_TOKEN=${GITHUB_TOKEN} \
                                  DB_MASTER_URL=${DB_URL} \
                                  DB_SLAVE_URL=${DB_URL} \
                                  ./gradlew clean build --no-daemon -Dspring.profiles.active=test

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
                // main 또는 dev 브랜치일 때 (PR은 제외, 파일 필터링 없음)
                anyOf {
                    branch 'main'
                    branch 'dev'
                }
            }
            stages {

                // === 5. Build & Push Docker Image (GString 문제 해결) ===
                stage('Build & Push Docker Image') {
                    steps {
                        withCredentials([
                            string(credentialsId: env.AWS_ACCOUNT_ID_CREDENTIALS_ID, variable: 'AWS_ACCOUNT_ID'),
                            usernamePassword(credentialsId: env.GPR_CREDENTIALS_ID, usernameVariable: 'GITHUB_ACTOR', passwordVariable: 'GITHUB_TOKEN')
                        ]) {
                            script {
                                // 1. Groovy 스크립트 영역에서 변수 정의
                                def ecrRegistryUri = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

                                if (ecrRegistryUri.contains("null")) {
                                    error "FATAL: 'aws-account-id' credential secret is empty or null!"
                                }

                                def imageTag = "${ecrRegistryUri}/${env.ECR_REPO_NAME}:${env.BUILD_NUMBER}"
                                def latestTag = "${ecrRegistryUri}/${env.ECR_REPO_NAME}:latest"

                                // 2. withEnv를 사용해 Shell 환경변수로 주입
                                withEnv([
                                    "ECR_REGISTRY_URI=${ecrRegistryUri}",
                                    "IMAGE_TAG=${imageTag}",
                                    "LATEST_TAG=${latestTag}",
                                    "REGION=${env.AWS_REGION}"
                                ]) {
                                    // 3. Shell 스크립트 실행 (''' 사용, 이스케이프 불필요)
                                    sh '''
                                        set -e
                                        # BuildKit/Buildx 충돌을 방지하기 위해 DOCKER_BUILDKIT 변수 unset
                                        unset DOCKER_BUILDKIT

                                        # GITHUB_TOKEN을 임시 파일로 저장 (secret 마운트를 위해)
                                        echo -n "$GITHUB_TOKEN" > github_token.tmp

                                        echo "🔐 Logging into ECR..."
                                        aws ecr get-login-password --region $REGION | docker login --username AWS --password-stdin $ECR_REGISTRY_URI

                                        echo "🏗️  Building Docker image using Buildx..."

                                        # 'docker buildx build' 명령을 명시적으로 사용
                                        # '--load' 플래그를 추가하여 이미지를 로컬 도커 캐시에 저장
                                        docker build \
                                            --secret id=github_token,src=github_token.tmp \
                                            -t $IMAGE_TAG -t $LATEST_TAG \
                                            --load .

                                        # 임시 파일 삭제
                                        rm github_token.tmp

                                        echo "📤 Pushing to ECR..."
                                        # 로컬 캐시에 저장된 이미지를 푸시
                                        docker push $IMAGE_TAG
                                        docker push $LATEST_TAG
                                    '''
                                } // end withEnv
                            } // end script
                        } // end withCredentials
                    }
                }

               // === 6. Deploy to ECS (GString 문제 해결 + GitHub Checks API 보고) ===
               stage('Deploy to ECS') {
                   steps {
                       withCredentials([string(credentialsId: env.AWS_ACCOUNT_ID_CREDENTIALS_ID, variable: 'AWS_ACCOUNT_ID')]) {
                           script {
                               // 1. Groovy 스크립트 영역에서 변수 정의
                               def ecrRegistryUri = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

                               if (ecrRegistryUri.contains("null")) {
                                   error "FATAL: 'aws-account-id' credential secret is empty or null!"
                               }

                               def imageUri = "${ecrRegistryUri}/${env.ECR_REPO_NAME}:${env.BUILD_NUMBER}"

                               // 2. withChecks 블록으로 배포 전체를 래핑합니다.
                               // 'Production Deployment'라는 이름으로 GitHub에 상태 체크를 보고합니다.
                               withChecks(name: 'Production Deployment') {
                                   try {
                                       // (withChecks가 자동으로 'PENDING'/'IN_PROGRESS' 상태를 보고합니다)

                                       // 3. withEnv를 사용해 Shell 환경변수로 주입
                                       withEnv([
                                           "CLUSTER_NAME=${env.ECS_CLUSTER_NAME}",
                                           "SERVICE_NAME=${env.ECS_SERVICE_NAME}",
                                           "TASK_DEFINITION_FAMILY=${env.ECS_TASK_DEFINITION_FAMILY}",
                                           "IMAGE_URI=${imageUri}",
                                           "REGION=${env.AWS_REGION}"
                                       ]) {
                                            // 4. 순수 Shell 스크립트 실행 (''' 사용)
                                            sh '''
                                                set -e

                                                echo "=========================================="
                                                echo "🚀 Starting Blue/Green Deployment (Service already configured)"
                                                echo "Service: $SERVICE_NAME"
                                                echo "New Image: $IMAGE_URI"
                                                echo "=========================================="

                                                echo "📋 Getting current task definition..."
                                                CURRENT_TASK_DEF=$(aws ecs describe-task-definition \
                                                    --task-definition $TASK_DEFINITION_FAMILY \
                                                    --region $REGION \
                                                    --query 'taskDefinition')

                                                echo "🔄 Creating new task definition with image: $IMAGE_URI"
                                                NEW_TASK_DEF=$(echo "$CURRENT_TASK_DEF" | jq --arg IMAGE "$IMAGE_URI" --arg CONTAINER_NAME "$ECS_CONTAINER_NAME" '
                                                    (.containerDefinitions[] | select(.name == $CONTAINER_NAME) | .image) = $IMAGE |
                                                    del(.taskDefinitionArn, .revision, .status, .requiresAttributes, .placementConstraints, .compatibilities, .registeredAt, .registeredBy)')

                                                echo "📝 Registering new task definition..."
                                                NEW_TASK_DEF_ARN=$(aws ecs register-task-definition \
                                                    --region $REGION \
                                                    --cli-input-json "$NEW_TASK_DEF" \
                                                    --query 'taskDefinition.taskDefinitionArn' \
                                                    --output text)
                                                echo "✅ New task definition: $NEW_TASK_DEF_ARN"

                                                echo "🚀 Initiating Blue/Green deployment..."
                                                aws ecs update-service \
                                                    --cluster $CLUSTER_NAME \
                                                    --service $SERVICE_NAME \
                                                    --task-definition $NEW_TASK_DEF_ARN \
                                                    --force-new-deployment \
                                                    --region $REGION
                                                echo "✅ Blue/Green deployment initiated!"

                                                # 배포 모니터링 (수동 while 루프 복원)
                                                echo "👀 Monitoring deployment progress... (Waiting for Bake Time and Blue termination)"
                                                TIMEOUT=2400
                                                ELAPSED=0
                                                while [ $ELAPSED -lt $TIMEOUT ]; do
                                                    SERVICE_INFO=$(aws ecs describe-services --cluster $CLUSTER_NAME --services $SERVICE_NAME --region $REGION --query 'services[0]')
                                                    DEPLOYMENT_STATUS=$(echo $SERVICE_INFO | jq -r '.deployments[0].status')
                                                    RUNNING_COUNT=$(echo $SERVICE_INFO | jq -r '.runningCount')
                                                    DESIRED_COUNT=$(echo $SERVICE_INFO | jq -r '.desiredCount')
                                                    DEPLOYMENTS=$(echo $SERVICE_INFO | jq -r '.deployments | length')

                                                    echo "[ $(date '+%H:%M:%S') ] Status: $DEPLOYMENT_STATUS | Running: $RUNNING_COUNT/$DESIRED_COUNT | Deployments: $DEPLOYMENTS"

                                                    # 배포가 완료(PRIMARY)되고 배포 개수가 1개(Blue 제거 완료)일 때
                                                    if [ "$DEPLOYMENT_STATUS" = "PRIMARY" ] && [ "$RUNNING_COUNT" = "$DESIRED_COUNT" ] && [ "$DEPLOYMENTS" = "1" ]; then
                                                        echo "🎉 Blue/Green deployment completed successfully!"
                                                        break
                                                    elif [ "$DEPLOYMENT_STATUS" = "FAILED" ]; then
                                                        echo "💥 Deployment failed!"
                                                        exit 1
                                                    fi

                                                    sleep 30
                                                    ELAPSED=$(( $ELAPSED + 30 ))
                                                done

                                                if [ $ELAPSED -ge $TIMEOUT ]; then
                                                    echo "⏰ Deployment timeout reached!"
                                                    exit 1
                                                fi
                                                echo "🎊 Deployment successful! New version is now serving traffic."
                                            '''
                                        } // end withEnv

                                       // 5. GitHub에 "배포 성공" 상태 보고
                                       publishChecks(
                                           status: 'COMPLETED',
                                           conclusion: 'SUCCESS',
                                           title: 'Deploy Success',
                                           summary: "Build #${env.BUILD_NUMBER} successfully deployed to Production."
                                       )

                                   } catch (e) {
                                       // 6. GitHub에 "배포 실패" 상태 보고
                                       publishChecks(
                                           status: 'COMPLETED',
                                           conclusion: 'FAILURE',
                                           title: 'Deploy Failed',
                                           summary: "Build #${env.BUILD_NUMBER} failed to deploy. Error: ${e.message}"
                                       )
                                       // Jenkins 빌드도 실패 처리
                                       throw e
                                   }
                               } // end withChecks
                           } // end script
                       } // end withCredentials
                   }
               } // end stage Deploy to ECS

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