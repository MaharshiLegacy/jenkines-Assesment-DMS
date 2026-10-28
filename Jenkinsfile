
pipeline {
    agent any

    tools {
        jdk 'java21'
    }

    environment {
        PROJECT_ID = 'project-f45bd8e2-3bd4-49b8-a29'
        REGION     = 'asia-south1'
        CLUSTER    = 'jenkins-gke-cluster'
        REPOSITORY = 'jenkins-dms-repository'
        IMAGE_NAME = 'dms-image'
        IMAGE_URI  = "${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPOSITORY}/${IMAGE_NAME}"
        NAMESPACE  = 'demo'
        DEPLOYMENT = 'jenkins-dms-repository'

        // This must match the application container name in deployment.yaml.
        CONTAINER_NAME = 'jenkins-dms-repository'
    }

    stages {
        stage('Verify Toolchain') {
            steps {
                sh '''
                    set -eu
                    chmod +x mvnw
                    echo "JAVA_HOME=$JAVA_HOME"
                    java -version
                    javac -version
                    ./mvnw -version
                    docker --version
                    kubectl version --client
                '''
            }
        }

        stage('Build and Test') {
            steps {
                sh './mvnw -B clean verify'
            }
            post {
                always {
                    junit allowEmptyResults: true,
                          testResults: 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                sh '''
                    set -eu
                    echo "Building image: ${IMAGE_URI}:${BUILD_NUMBER}"

                    docker build \
                        -t "${IMAGE_URI}:${BUILD_NUMBER}" \
                        -t "${IMAGE_URI}:latest" .
                '''
            }
        }

        stage('Push Image to Artifact Registry') {
            steps {
                sh '''
                    set -eu

                    gcloud auth configure-docker \
                        "${REGION}-docker.pkg.dev" --quiet

                    docker push "${IMAGE_URI}:${BUILD_NUMBER}"
                    docker push "${IMAGE_URI}:latest"

                    echo "Pushed: ${IMAGE_URI}:${BUILD_NUMBER}"
                '''
            }
        }

        stage('Deploy to GKE') {
            steps {
                sh '''
                    set -eu

                    gcloud config set project "${PROJECT_ID}"

                    gcloud container clusters get-credentials "${CLUSTER}" \
                        --region="${REGION}" \
                        --project="${PROJECT_ID}"

                    kubectl get secret dms-secrets \
                        -n "${NAMESPACE}" >/dev/null

                    kubectl get secret dms-db-secrets \
                        -n "${NAMESPACE}" >/dev/null

                    # Save the currently deployed app image for recovery.
                    OLD_IMAGE=$(kubectl get deployment "${DEPLOYMENT}" \
                        -n "${NAMESPACE}" \
                        -o "jsonpath={.spec.template.spec.containers[?(@.name==\\"${CONTAINER_NAME}\\")].image}")

                    if [ -z "$OLD_IMAGE" ]; then
                        echo "ERROR: Could not find the application container."
                        echo "Check CONTAINER_NAME against k8s/deployment.yaml."
                        exit 1
                    fi

                    echo "Previous app image: $OLD_IMAGE"
                    echo "New app image: ${IMAGE_URI}:${BUILD_NUMBER}"

                    # Apply storage and Service resources.
                    kubectl apply -n "${NAMESPACE}" \
                        -f k8s/pvc.yaml \
                        -f k8s/service.yaml

                    # Update ONLY the Spring Boot container.
                    # The Cloud SQL Auth Proxy image and configuration stay unchanged.
                    kubectl set image \
                        deployment/"${DEPLOYMENT}" \
                        "${CONTAINER_NAME}=${IMAGE_URI}:${BUILD_NUMBER}" \
                        -n "${NAMESPACE}"

                    # Wait for the new ReplicaSet to become ready.
                    if ! kubectl rollout status \
                        deployment/"${DEPLOYMENT}" \
                        -n "${NAMESPACE}" \
                        --timeout=300s; then

                        echo "Deployment failed. Collecting diagnostic information..."

                        kubectl get pods -n "${NAMESPACE}" -o wide || true
                        kubectl describe deployment "${DEPLOYMENT}" \
                            -n "${NAMESPACE}" || true
                        kubectl logs deployment/"${DEPLOYMENT}" \
                            -n "${NAMESPACE}" \
                            -c "${CONTAINER_NAME}" \
                            --tail=100 || true

                        echo "Restoring previous app image: $OLD_IMAGE"

                        kubectl set image \
                            deployment/"${DEPLOYMENT}" \
                            "${CONTAINER_NAME}=${OLD_IMAGE}" \
                            -n "${NAMESPACE}" || true

                        kubectl rollout status \
                            deployment/"${DEPLOYMENT}" \
                            -n "${NAMESPACE}" \
                            --timeout=180s || true

                        exit 1
                    fi
                '''
            }
        }

        stage('Verify Deployment') {
            steps {
                sh '''
                    set -eu

                    echo "Deployment status:"
                    kubectl get deployment "${DEPLOYMENT}" \
                        -n "${NAMESPACE}"

                    echo "Application image:"
                    kubectl get deployment "${DEPLOYMENT}" \
                        -n "${NAMESPACE}" \
                        -o "jsonpath={.spec.template.spec.containers[?(@.name==\\"${CONTAINER_NAME}\\")].image}"
                    echo

                    echo "Pods:"
                    kubectl get pods -n "${NAMESPACE}" -o wide

                    echo "Services and PVCs:"
                    kubectl get svc,pvc -n "${NAMESPACE}"
                '''
            }
        }
    }

    post {
        success {
            echo 'CI/CD pipeline completed successfully.'
        }
        failure {
            echo 'CI/CD pipeline failed. Check the failed stage and Jenkins console output.'
        }
    }
}
