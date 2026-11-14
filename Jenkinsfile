// Chaîne CI/CD de socle-testapp
// build + tests unitaires → Sonar (porte qualité) → JAR dans Nexus → image Docker dans Nexus
// → déploiement sur testapp.home.arpa:8085 → contrôle de fumée via le Grid
// Toutes les versions (JAR et images) sont conservées dans Nexus.
//
// Règle : tout est compilé et le JAR est fabriqué UNE SEULE FOIS, à la première étape.
// Les étapes suivantes réutilisent ce qui a été produit (JAR analysé = JAR publié = JAR déployé ;
// le contrôle de fumée exécute les classes de test déjà compilées, sans relancer le cycle Maven).
// Maven avec le settings.xml géré par Jenkins (plugin Config File Provider, fichier « maven-settings-nexus ») :
// miroir Nexus et identifiants injectés par Jenkins, aucun fichier de configuration CI dans le projet.
// Avec codeRetour = true, renvoie le code de sortie de Maven au lieu d'arrêter le pipeline en cas d'échec.
def mvn(String arguments, boolean codeRetour = false) {
    def code = null
    configFileProvider([configFile(fileId: 'maven-settings-nexus', variable: 'MAVEN_SETTINGS')]) {
        code = sh(returnStatus: codeRetour, script: "mvn -B -ntp -s \"\$MAVEN_SETTINGS\" -Drevision=${env.VERSION} ${arguments}")
    }
    return code
}

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
        string(name: 'SESSIONS_PAR_NAVIGATEUR', defaultValue: 'auto',
               description: 'Nombre de navigateurs ouverts en parallèle pour chacun de Chrome, Firefox et Edge. « auto » = nœuds libres du Grid ÷ 3 (au moins 1).')
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
        APP_CONTAINER = 'socle-testapp'
        APP_PORT    = '8085'
        GRID_URL    = 'http://grid.home.arpa:4444'
        // Docker sans root de l'utilisateur jenkins-agent
        DOCKER_HOST = 'unix:///run/user/999/docker.sock'
    }

    stages {
        stage('Build et tests unitaires') {
            steps {
                mvn 'clean verify'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Analyse Sonar') {
            steps {
                withCredentials([string(credentialsId: 'sonarqube-ci', variable: 'SONAR_TOKEN')]) {
                    script {
                        // Le scanner lit SONAR_TOKEN dans l'environnement et attend le verdict de la porte qualité.
                        // Porte non franchie : build instable (jaune) mais le pipeline continue, comme un test en échec.
                        // Analyse impossible (pas de rapport déposé dans Sonar) : le build échoue.
                        int code = mvn("sonar:sonar -Dsonar.host.url=${env.SONAR_URL} -Dsonar.qualitygate.wait=true -Dsonar.qualitygate.timeout=300", true)
                        if (code != 0) {
                            if (!fileExists('target/sonar/report-task.txt')) {
                                error 'Analyse Sonar impossible : aucun rapport transmis au serveur'
                            }
                            unstable "Porte qualité Sonar non franchie : ${env.SONAR_URL}/dashboard?id=fr.socle%3Asocle-testapp"
                        }
                    }
                }
            }
        }

        stage('Publication du JAR') {
            steps {
                // Publie le JAR déjà fabriqué (et son POM aplati) sans relancer le cycle Maven
                mvn "deploy:deploy-file -Dfile=target/socle-testapp.jar -DpomFile=.flattened-pom.xml -DrepositoryId=nexus -Durl=${env.NEXUS_URL}/repository/maven-releases/"
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

        stage('Déploiement') {
            steps {
                script {
                    // Conteneur unique publié sur le port de l'application ; en cas d'échec, retour à l'image précédente
                    def previous = sh(returnStdout: true,
                            script: 'docker inspect -f "{{.Config.Image}}" "$APP_CONTAINER" 2>/dev/null || true').trim()
                    echo "Version en place : ${previous ?: 'aucune'}"
                    def start = { String image ->
                        sh """
                            docker rm -f "\$APP_CONTAINER" >/dev/null 2>&1 || true
                            docker run -d --name "\$APP_CONTAINER" --restart unless-stopped \
                                -p "\$APP_PORT:8080" --memory 512m "${image}" >/dev/null
                        """
                    }
                    def answers = {
                        sh(returnStatus: true,
                           script: 'curl -fsS "http://127.0.0.1:$APP_PORT/api/ping?delay=0" >/dev/null 2>&1') == 0
                    }

                    start(env.IMAGE)
                    try {
                        timeout(time: 2, unit: 'MINUTES') {
                            waitUntil(initialRecurrencePeriod: 2000) { answers() }
                        }
                        echo "Déployé : ${env.IMAGE}"
                    } catch (err) {
                        sh 'docker logs --tail 100 "$APP_CONTAINER" || true'
                        if (previous) {
                            echo "Retour à la version précédente : ${previous}"
                            start(previous)
                        }
                        error("Échec du déploiement de ${env.IMAGE} : l'application ne répond pas")
                    }
                }
            }
        }

        stage('Contrôle de fumée') {
            steps {
                script {
                    // Les trois navigateurs en parallèle ; chacun répartit ses pages sur plusieurs sessions.
                    // Chaque session travaille dans son propre espace de données de l'application : aucune interférence.
                    // Ordre de priorité : les navigateurs les plus lents reçoivent en premier les nœuds restants
                    def navigateurs = ['chrome', 'firefox', 'edge']
                    def priorite = ['edge', 'chrome', 'firefox']
                    def sessions = [:]
                    String demande = (params.SESSIONS_PAR_NAVIGATEUR ?: 'auto').trim()
                    if (demande.isInteger()) {
                        navigateurs.each { sessions[it] = Math.max(1, demande.toInteger()) }
                    } else {
                        // Capacité libre réelle : par nœud disponible, sessions maximales moins sessions en cours
                        int libres = sh(returnStdout: true, script: '''
                            curl -fsS "$GRID_URL/status" | python3 -c "import json, sys; v = json.load(sys.stdin)['value']; print(sum(max(0, n.get('maxSessions', 1) - sum(1 for s in n['slots'] if s.get('session'))) for n in v['nodes'] if n.get('availability') == 'UP'))"
                        ''').trim().toInteger()
                        echo "Nœuds libres sur le Grid : ${libres}"
                        // Toute la capacité est répartie : part égale, puis le reste aux plus lents (au moins 1 chacun)
                        int part = libres.intdiv(navigateurs.size())
                        int reste = libres % navigateurs.size()
                        priorite.eachWithIndex { nav, i -> sessions[nav] = Math.max(1, part + (i < reste ? 1 : 0)) }
                    }
                    echo "Sessions par navigateur : ${sessions}"

                    def branches = [:]
                    for (n in navigateurs) {
                        def browser = n
                        branches["Fumée ${browser}"] = {
                            catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
                                // Exécution seule des tests de fumée déjà compilés (étape 1) : ni compilation,
                                // ni fabrication du JAR, ni couverture. Rapports propres au navigateur
                                // (failsafe-reports-<navigateur>, allure-results-<navigateur>).
                                sh "rm -rf target/failsafe-reports-${browser} target/failsafe-summary-${browser}.xml target/allure-results-${browser}"
                                mvn("-Psmoke failsafe:integration-test failsafe:verify" +
                                    " -Dbrowser=${browser} -Dsmoke.sessions=${sessions[browser]}" +
                                    " -Dapp.url=${env.APP_URL} -Dgrid.url=${env.GRID_URL}" +
                                    " -Dallure.results.directory=${env.WORKSPACE}/target/allure-results-${browser}" +
                                    " -Dsmoke.ralenti=${params.RALENTI_MS ?: '0'} -Dapp.expectedVersion=${env.VERSION}")
                            }
                            // Rapports JUnit préfixés par le navigateur pour les distinguer dans Jenkins
                            sh """
                                for f in target/failsafe-reports-${browser}/TEST-*.xml; do
                                    [ -e "\$f" ] || continue
                                    sed -i 's/classname="/classname="${browser}./g' "\$f"
                                done
                            """
                        }
                    }
                    parallel branches
                }
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'target/failsafe-reports-*/TEST-*.xml'
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
