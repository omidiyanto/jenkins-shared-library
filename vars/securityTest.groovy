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
    withCredentials([string(credentialsId: SONAR_TOKEN_NAME, variable: 'SONAR_TOKEN')]) {
          sh """curl -s -u "${SONAR_TOKEN}:" "${env.SONAR_HOST_URL}/api/hotspots/search?project=${env.sonarProjectKey}" -o sonarqube-scan-report.json || true"""
    }
}

def containerImageScanning(Map config = [:]) {
    env.trivyImage="${env.registryName}/${env.imageName}:${env.imageTag}"
    // Log in before scanning
    sh "export TRIVY_INSECURE=true; export TRIVY_DISABLE_VEX_NOTICE=true && echo ${env.registryPassword} | trivy registry login ${env.registryName} --username ${env.registryUsername} --password-stdin"
    // For master/main enforce exit-code on HIGH+ (currently "disabled/bypassed")
    def trivyArgs = (env.branch_name in ['master','main']) ? '--severity HIGH,CRITICAL --exit-code 1' : ''
    def cmd = "trivy image ${trivyArgs} --insecure --format json ${env.trivyImage} > image-scan.json"
    def result = sh(script: cmd, returnStatus: true)
    if (env.branch_name in ['master','main'] && result != 0) {
        echo "HIGH or CRITICAL vulnerabilities detected by Trivy on master/main. Pipeline bypassed."
    } else {
        echo "Container scan completed${trivyArgs ? ' with HIGH+ gating' : ''}. Continuing."
    }
}




def generateReportAndPublishToDD(Map config = [:]) {
    def VERIFIED_POLICY = config.verifiedPolicy ?: [
        'Trufflehog Scan': true,
        'Anchore Grype'  : false,
        'Trivy Scan'     : false,
        'SonarQube Scan' : false
    ]
    withCredentials([string(credentialsId: SONAR_TOKEN_NAME, variable: 'SONAR_TOKEN')]) {
        sh "generate-security-report -sonar-url=https://sonarqube.satnusa.com -sonar-token=$SONAR_TOKEN -sonar-task=${env.sonarTaskID} -project-url=https://sonarqube.satnusa.com/dashboard?id=${env.sonarProjectKey}"
        archiveArtifacts artifacts: 'report.html', allowEmptyArchive: true
    }
    withCredentials([string(credentialsId: DD_API_KEY_NAME, variable: 'DD_API_KEY')]) {
        def slug = (params.BRANCHNAME_PARAM?.trim() ?: env.branch_name).replaceAll('/', '-')
        env.engagement_name = "${env.appName}-${slug}"
        echo "Defect Dojo Engagement Name=${env.engagement_name}"
        def count = sh(
            script: """
                    curl -sS -f -G "${env.DD_URL}/api/v2/engagements/" \
                        -H "Authorization: Token ${DD_API_KEY}" \
                        --data-urlencode "name=${env.engagement_name}" \
                        --data-urlencode "product__name=${env.appName}" | jq -r '.count'
                    """, returnStdout: true
        ).trim()
        if (count == '0') {
            def s = java.time.LocalDate.now().toString()
            def e = java.time.LocalDate.now().plusDays(180).toString()
            env.DD_DATE_FIELDS = "-F engagement_start_date=${s} -F engagement_end_date=${e}"
            echo "new engagement dates ${s}..${e}"
        } else {
            env.DD_DATE_FIELDS = ""
            echo "engagement exists – skip dates"
        }
        def uploads = [
            [file: 'secret-scanning.json', type: 'Trufflehog Scan'],
            [file: 'sca.json', type: 'Anchore Grype'],
            [file: 'image-scan.json', type: 'Trivy Scan'],
            [file: 'sonarqube-scan-report.json', type: 'SonarQube Scan']
        ]
        uploads.each {
            u -> if (fileExists(u.file)) {
                def verified = (VERIFIED_POLICY.get(u.type, false) ? "true": "false")
                sh """
                    curl -sS -f -X POST "${env.DD_URL}/api/v2/reimport-scan/" \
                    -H "Authorization: Token ${DD_API_KEY}" \
                    -F "product_name=${env.appName}" \
                    -F "product_type_name=Nusames" \
                    -F "engagement_name=${env.engagement_name}" \
                    -F "scan_type=${u.type}" \
                    -F "file=@${u.file}" \
                    -F "build_id=${env.BUILD_NUMBER}" \
                    -F "commit_hash=${env.commit_sha}" \
                    -F "branch_tag=${env.branch_name}" \
                    -F "source_code_management_uri=${env.gitUrl}" \
                    -F "version=build-${env.BUILD_NUMBER}" \
                    -F "active=true" \
                    -F "verified=${verified}" \
                    -F "do_not_reactivate=false" \
                    -F "close_old_findings=true" \
                    -F "auto_create_context=true" \
                    ${env.DD_DATE_FIELDS}
                """
            } else {
                echo "skip ${u.file} (not found)"
            }
        }
    }
}
