// Chaîne CI/CD de socle-testapp
// build + tests unitaires → Sonar (porte qualité) → JAR dans Nexus → image Docker dans Nexus
// → rétention (3 images) → déploiement sur testapp.home.arpa:8085 → contrôle de fumée via le Grid
pipeline {
    agent any

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        disableConcurrentBuilds()
        timestamps()
        timeout(time: 60, unit: 'MINUTES')
    }

    triggers {
        githubPush()
    }

    parameters {
        string(name: 'RALENTI_MS', defaultValue: '0',
               description: 'Mode observation du contrôle de fumée : pause en ms après chaque geste (ex. 800) pour suivre le déroulé dans le Grid. 0 = vitesse normale.')
    }

    tools {
        jdk 'jdk21'
        maven 'maven-3.9'
    }

    environment {
        VERSION     = "1.0.${env.BUILD_NUMBER}"
        NEXUS_URL   = 'http://nexus.home.arpa:8083'
        REGISTRY    = 'nexus.home.arpa:8084'
        IMAGE       = "${REGISTRY}/socle-testapp:${VERSION}"
        SONAR_URL   = 'http://sonarqube.home.arpa:9000'
        APP_URL     = 'http://testapp.home.arpa:8085'
        GRID_URL    = 'http://grid.home.arpa:4444'
        // Docker sans root de l'utilisateur jenkins-agent
        DOCKER_HOST = 'unix:///run/user/999/docker.sock'
        MVN         = "mvn -B -ntp -s ci/settings.xml -Drevision=${VERSION}"
    }

    stages {
        stage('Build et tests unitaires') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'nexus-ci', usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASSWORD')]) {
                    sh '$MVN clean verify'
                }
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Analyse Sonar') {
            steps {
                withCredentials([
                        usernamePassword(credentialsId: 'nexus-ci', usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASSWORD'),
                        string(credentialsId: 'sonarqube-ci', variable: 'SONAR_TOKEN')]) {
                    // Le scanner lit SONAR_TOKEN dans l'environnement et attend le verdict de la porte qualité
                    sh '$MVN sonar:sonar -Dsonar.host.url=$SONAR_URL -Dsonar.qualitygate.wait=true -Dsonar.qualitygate.timeout=300'
                }
            }
        }

        stage('Publication du JAR') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'nexus-ci', usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASSWORD')]) {
                    sh '$MVN -DskipTests deploy -DaltDeploymentRepository=nexus::$NEXUS_URL/repository/maven-releases/'
                }
            }
        }

        stage('Image Docker') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'nexus-ci', usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASSWORD')]) {
                    // Configuration Docker propre au build : l'identifiant ne reste pas sur l'agent
                    withEnv(["DOCKER_CONFIG=${env.WORKSPACE}/.docker-ci"]) {
                        sh '''
                            docker build --pull -t "$IMAGE" .
                            echo "$NEXUS_PASSWORD" | docker login "$REGISTRY" -u "$NEXUS_USER" --password-stdin
                            docker push "$IMAGE"
                            docker logout "$REGISTRY"
                        '''
                    }
                }
            }
        }

        stage('Rétention Nexus') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'nexus-ci', usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASSWORD')]) {
                    sh 'ci/nexus-docker-retention.sh socle-testapp 3'
                }
            }
        }

        stage('Déploiement') {
            steps {
                sh 'ci/deploy.sh "$IMAGE"'
            }
        }

        stage('Contrôle de fumée') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'nexus-ci', usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASSWORD')]) {
                    script {
                        // Navigateurs l'un après l'autre : les tests modifient les données de l'application
                        for (browser in ['chrome', 'firefox', 'edge']) {
                            stage("Fumée ${browser}") {
                                catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
                                    // jacoco.skip : la couverture ne concerne que les tests unitaires (étape 1) ;
                                    // ici les tests pilotent un navigateur contre l'application déployée.
                                    sh """
                                        rm -rf target/failsafe-reports target/allure-results-${browser}
                                        \$MVN -Psmoke -DskipUnitTests=true -Djacoco.skip=true verify \
                                            -Dbrowser=${browser} -Dapp.url=\$APP_URL -Dgrid.url=\$GRID_URL \
                                            -Dallure.results.directory=\$WORKSPACE/target/allure-results-${browser} \
                                            -Dsmoke.ralenti=${params.RALENTI_MS ?: '0'}
                                    """
                                }
                                // Rapports préfixés par le navigateur pour les distinguer dans Jenkins
                                sh """
                                    mkdir -p target/smoke-${browser}
                                    for f in target/failsafe-reports/TEST-*.xml; do
                                        [ -e "\$f" ] || continue
                                        sed 's/classname="fr\\.socle\\.testapp\\.smoke\\./classname="${browser}./g' "\$f" > "target/smoke-${browser}/\$(basename "\$f")"
                                    done
                                """
                            }
                        }
                    }
                }
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'target/smoke-*/*.xml'
                    // Rapport HTML Allure (plugin Jenkins Allure + outil « allure » déclaré dans Tools)
                    allure includeProperties: false, jdk: '', results: [
                            [path: 'target/allure-results-chrome'],
                            [path: 'target/allure-results-firefox'],
                            [path: 'target/allure-results-edge']]
                }
            }
        }
    }

    post {
        always {
            sh 'rm -rf .docker-ci'
        }
        success {
            echo "Version ${env.VERSION} déployée : ${env.APP_URL}/classic/"
        }
    }
}
