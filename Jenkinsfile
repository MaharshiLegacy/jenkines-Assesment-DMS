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
        DEPLOYMENT = 'jenkins-dms-repository'   // must match metadata.name in k8s/deployment.yaml
    }

    stages {

        // Declarative pipelines already check out the repo automatically, so no explicit Checkout stage is needed.

        stage('Verify Toolchain') {
            steps {
                sh '''
                    chmod +x mvnw
                    echo "JAVA_HOME=$JAVA_HOME"
                    java -version
                    javac -version
                    ./mvnw -version
                '''
            }
        }

        stage('Build and Test') {
            steps {
                sh './mvnw -B clean verify'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                sh '''
                    set -eu
                    echo "Building Docker image: ${IMAGE_URI}:${BUILD_NUMBER}"
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
                    gcloud auth configure-docker "${REGION}-docker.pkg.dev" --quiet
                    docker push "${IMAGE_URI}:${BUILD_NUMBER}"
                    docker push "${IMAGE_URI}:latest"
                    echo "Image pushed: ${IMAGE_URI}:${BUILD_NUMBER}"
                '''
            }
        }

        stage('Deploy to GKE') {
            steps {
                sh '''
                    set -eu

                    gcloud config set project "${PROJECT_ID}"
                    gcloud container clusters get-credentials "${CLUSTER}" \
                        --region="${REGION}" --project="${PROJECT_ID}"

                    kubectl create namespace "${NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

                    # The app needs this Secret (create it once, see notes). Fail early with a clear message.
                    kubectl get secret dms-secrets -n "${NAMESPACE}" >/dev/null 2>&1 || {
                        echo "ERROR: Secret dms-secrets is missing in namespace ${NAMESPACE}"; exit 1; }

                    # Disk and Service first, then the Deployment with THIS build's unique image tag
                    kubectl apply -n "${NAMESPACE}" -f k8s/pvc.yaml -f k8s/service.yaml
                    sed "s|image: .*|image: ${IMAGE_URI}:${BUILD_NUMBER}|" k8s/deployment.yaml \
                        | kubectl apply -n "${NAMESPACE}" -f -

                    # Wait for the new pod; if it never becomes ready, show why and roll back
                    kubectl rollout status deployment/"${DEPLOYMENT}" -n "${NAMESPACE}" --timeout=300s || {
                        echo "Rollout failed. Pod status and last logs:"
                        kubectl get pods -n "${NAMESPACE}" -o wide || true
                        kubectl logs deployment/"${DEPLOYMENT}" -n "${NAMESPACE}" --tail=50 || true
                        kubectl rollout undo deployment/"${DEPLOYMENT}" -n "${NAMESPACE}" || true
                        exit 1
                    }
                '''
            }
        }

        stage('Verify Deployment') {
            steps {
                sh '''
                    set -eu
                    kubectl get deployment "${DEPLOYMENT}" -n "${NAMESPACE}"
                    kubectl get pods -n "${NAMESPACE}" -o wide
                    kubectl get svc,pvc -n "${NAMESPACE}"
                    kubectl get deployment "${DEPLOYMENT}" -n "${NAMESPACE}" \
                        -o jsonpath='{.spec.template.spec.containers[0].image}'
                    echo
                '''
            }
        }
    }
}
