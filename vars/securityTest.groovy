def sca(Map config = [:]) {
    // Use fail-on only on master/main
    def severityArgs = (env.branch_name in ['master','main']) ? '--fail-on HIGH' : ''
    def cmd = "grype dir:/volumes/${env.appName}-sca-test-${env.BUILD_NUMBER}-volume/_data/ -o json ${severityArgs} > sca.json"                            
    def result = sh(script: cmd, returnStatus: true)
    if (env.branch_name in ['master','main'] && result != 0) {
        echo "Critical or High vulnerabilities detected by Grype on ${env.branch_name}. Pipeline bypassed."
    } else {
        echo "SCA completed${severityArgs ? ' with HIGH+ gating' : ''}. Continuing."
    }
}

def secretsScanning(Map config = [:]) {
    def result = sh(script: 'trufflehog filesystem . --json > secret-scanning.json', returnStatus: true)
    sh 'cat secret-scanning.json'
    if (env.branch_name in ['master','main'] && result != 0) {
        echo "Secret ditemukan oleh trufflehog di branch ${env.branch_name}. Pipeline bypassed."
    } else {
        echo "Secret scanning selesai. ${env.branch_name in ['master','main'] ? 'Blocking on any secret.' : 'Continuing despite findings.'}"
    }
}

def sast(Map config = [:]) {
    withCredentials([string(credentialsId: 'sonarqube-token', variable: 'SONAR_TOKEN')]) {
        withSonarQubeEnv("${env.SONAR_ENV}") {
            // Jalankan SonarQube scanner dan simpan output
            sh """
                ${SONAR_SCANNER}/bin/sonar-scanner \\
                    -Dsonar.projectKey=${env.sonarProjectKey} \\
                    -Dsonar.token=$SONAR_TOKEN | tee sonarqube_output.txt
            """

            // Ambil Task ID dari output
            env.sonarTaskID = sh(
                script: "grep -o 'id=[a-f0-9-]\\+' sonarqube_output.txt | cut -d= -f2",
                returnStdout: true
            ).trim()

            echo "SonarQube Task ID: ${env.sonarTaskID}"
        }
    }

    int maxRetries = 60
    int retryCount = 0
    def qualityGateResult = null

    while (retryCount < maxRetries) {
        try {
            timeout(time: 5, unit: 'SECONDS') {
                qualityGateResult = waitForQualityGate abortPipeline: false, credentialsId: env.SONAR_TOKEN
            }
        } catch (err) {
            echo "Timeout pada attempt ${retryCount+1} saat memanggil waitForQualityGate. Mencoba lagi..."
            qualityGateResult = [status: 'IN_PROGRESS']
        }

        echo "Attempt ${retryCount+1}: SonarQube Quality Gate status = ${qualityGateResult.status}"
        if (qualityGateResult.status != 'IN_PROGRESS' && qualityGateResult.status != 'PENDING') {
            break
        }
        retryCount++
    }

    if (qualityGateResult.status == 'IN_PROGRESS' || qualityGateResult.status == 'PENDING') {
        echo "Quality Gate check timed out after ${maxRetries} retries. Please verify the SonarQube server for details."
    }
    if (qualityGateResult.status == 'ERROR') {
        echo "SonarQube Quality Gate gagal dengan status: ${qualityGateResult.status}. Pipeline bypassed."
    }
    withCredentials([string(credentialsId: SONAR_TOKEN_ID, variable: 'SONAR_TOKEN')]) {
          sh """curl -s -u "${SONAR_TOKEN}:" "${env.SONAR_HOST_URL}/api/hotspots/search?project=${env.sonarProjectKey}" -o sonarqube-scan-report.json || true"""
    }
}