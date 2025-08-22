def uat(Map config = [:]) {
    // For develop, hotfix, or release branches
    if (env.branch_name == "develop" || env.branch_name.startsWith("develop/")) {
        sh "sed -i 's|tag: .*|tag: ${env.imageTag}|g' chart-values/values-develop.yaml"
        sh "git add ."
        sh "git commit -m '[Jenkins] Updating develop image version to ${env.imageTag}'"
        withCredentials([usernamePassword(credentialsId: env.gitCredentials, passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
            sh "git push http://${GIT_USERNAME}:${GIT_PASSWORD}@${env.gitOpsRepo.replace('http://', '')}"
        }
    }
    else if (env.branch_name == "hotfix" || env.branch_name.startsWith("hotfix/")) {
        sh "sed -i 's|tag: .*|tag: ${env.imageTag}|g' chart-values/values-hotfix.yaml"
        sh "git add ."
        sh "git commit -m '[Jenkins] Updating hotfix image version to ${env.imageTag}'"
        withCredentials([usernamePassword(credentialsId: env.gitCredentials, passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
            sh "git push http://${GIT_USERNAME}:${GIT_PASSWORD}@${env.gitOpsRepo.replace('http://', '')}"
        }
    }
    else if (env.branch_name == "master" || env.branch_name == "main" || env.branch_name == "release" 
        || env.branch_name.startsWith("release/")) {
        echo "Deploying to Staging Environment (UAT - Release)"
        sh "sed -i 's|tag: .*|tag: ${env.imageTag}|g' chart-values/values-release.yaml"
        sh "git add ."
        sh "git commit -m '[Jenkins] Updating release image version to ${env.imageTag}'"
        withCredentials([usernamePassword(credentialsId: env.gitCredentials, passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
            sh "git push http://${GIT_USERNAME}:${GIT_PASSWORD}@${env.gitOpsRepo.replace('http://', '')}"
        }
    }
    // Healthcheck Using ArgoCD
    if (env.branch_name == "main" || env.branch_name == "master") {
        env.argoAppName = "${env.appName}-uat-release"
    } else {
        env.argoAppName = "${env.appName}-uat-${env.branch_name.split('/')[0].toLowerCase()}"
    }
    sh "argocd app sync ${env.argoAppName}"
    def waitResult = sh(script: "argocd app wait ${env.argoAppName} --sync --health --timeout 900", returnStatus: true)
    def healthStatus = sh(script: "argocd app get ${env.argoAppName} -o json | jq -r '.status.health.status'", returnStdout: true).trim()
    echo "Application Health Status: ${healthStatus} (by ArgoCD Health Check)"
    if (waitResult != 0 || healthStatus != 'Healthy') {
        echo "Application is not healthy or timed out while doing health check. Aborting pipeline."
        sendEmailTemplate(
            MAILMODE: 'UAT_UNHEALTHY',
            RECIPIENT_EMAIL: env.authorEmail,
            RECIPIENT_NAME: env.authorName,
            EXTRA_DATA: [healthStatus: healthStatus],
            ATTACHMENT_PATTERN: 'report.html'
        )
        error "Application is not healthy or wait timed out. Pipeline aborted."
    } else {
        echo "Application is healthy. Proceeding to next stage."
    }
}

def prod(Map config = [:]) {
    // 1. Send approval email to first approver
    sendEmailTemplate(
        MAILMODE: 'APPROVAL_1',
        RECIPIENT_EMAIL: env.firstApproverEmail,
        RECIPIENT_NAME: env.firstApproverID,
        ATTACHMENT_PATTERN: 'report.html'
    )
                        
    // 2. First approver approval
    def firstApprover = ""
    def firstDecision = ""
                        
    // Get first approval
    def validFirstApprover = false
    while (!validFirstApprover) {
        try {
            def firstApproval = input(
                id: 'firstApproval',
                message: "(${env.firstApproverID}): Approve deployment to production?",
                submitter: env.firstApproverID,
                submitterParameter: 'approver1',
                parameters: [
                    choice(name: 'approval1', choices: ['Approve', 'Reject'], description: 'Choose Approve to deploy into Production')
                ]
            )
            firstApprover = firstApproval['approver1']
            firstDecision = firstApproval['approval1']
            echo "First approval by: ${firstApprover} with decision: ${firstDecision}"
            // Check if first approval was rejected
            if (firstDecision == "Reject") {
                echo "Deployment to Production was rejected by the first approver."
                error "Deployment cancelled: Rejected by ${firstApprover}."
            }
            // Validate that first approver is actually the designated first approver
            if (firstApprover != env.firstApproverID) {
                echo "Warning: Approval was provided by ${firstApprover}, but should be from ${env.firstApproverID}. Please try again."
                continue
            }
            validFirstApprover = true
        } catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
            echo "Deployment process was aborted by user."
            error "Deployment aborted by user action."
        } catch (Exception e) {
            echo "Error during first approval: ${e.message}"
        }
    }
                        
    // 3. Send email to second approver
    sendEmailTemplate(
        MAILMODE: 'APPROVAL_2',
        RECIPIENT_EMAIL: env.secondApproverEmail,
        RECIPIENT_NAME: env.secondApproverID,
        EXTRA_DATA: [firstApprover: firstApprover],
        ATTACHMENT_PATTERN: 'report.html'
    )
                        
    // 4. Second approver approval
    def secondApprover = ""
    def secondDecision = ""
                        
    // Get second approval
    def validSecondApprover = false
    def selectedDepartments = []
    while (!validSecondApprover) {
        try {
            def secondApproval = input(
                id: 'secondApproval',
                message: "(${env.secondApproverID}): Approve deployment to production and select departments?",
                submitter: env.secondApproverID,
                submitterParameter: 'approver2',
                parameters: [
                    choice(name: 'approval2', choices: ['Approve', 'Reject'], description: 'Choose Approve to deploy into Production'),
                    booleanParam(name: 'deploy_asus', defaultValue: false, description: 'Deploy asus?'),
                    booleanParam(name: 'deploy_asusnb', defaultValue: false, description: 'Deploy asusnb?'),
                    booleanParam(name: 'deploy_desaysv', defaultValue: false, description: 'Deploy desaysv?'),
                    booleanParam(name: 'deploy_digi', defaultValue: false, description: 'Deploy digi?'),
                    booleanParam(name: 'deploy_hp', defaultValue: false, description: 'Deploy hp?'),
                    booleanParam(name: 'deploy_lenovo', defaultValue: false, description: 'Deploy lenovo?'),
                    booleanParam(name: 'deploy_motorola', defaultValue: false, description: 'Deploy motorola?'),
                    booleanParam(name: 'deploy_nauto', defaultValue: false, description: 'Deploy nauto?'),
                    booleanParam(name: 'deploy_tcl', defaultValue: false, description: 'Deploy tcl?'),
                    booleanParam(name: 'deploy_toa', defaultValue: false, description: 'Deploy toa?'),
                    booleanParam(name: 'deploy_xiaomi', defaultValue: false, description: 'Deploy xiaomi?')
                ]
            )
            secondApprover = secondApproval['approver2']
            secondDecision = secondApproval['approval2']
            // Kumpulkan departemen yang dicentang
            def deptList = [
                [key: 'deploy_asus', name: 'asus'],
                [key: 'deploy_asusnb', name: 'asusnb'],
                [key: 'deploy_desaysv', name: 'desaysv'],
                [key: 'deploy_digi', name: 'digi'],
                [key: 'deploy_hp', name: 'hp'],
                [key: 'deploy_lenovo', name: 'lenovo'],
                [key: 'deploy_motorola', name: 'motorola'],
                [key: 'deploy_nauto', name: 'nauto'],
                [key: 'deploy_tcl', name: 'tcl'],
                [key: 'deploy_toa', name: 'toa'],
                [key: 'deploy_xiaomi', name: 'xiaomi']
            ]
            selectedDepartments = deptList.findAll {
                secondApproval[it.key] == true
            }.collect {
                it.name
            }
            echo "Second approval by: ${secondApprover} with decision: ${secondDecision}"
            echo "Selected departments: ${selectedDepartments}"
            // Check if second approval was rejected
            if (secondDecision == "Reject") {
                echo "Deployment to Production was rejected by the second approver."
                error "Deployment cancelled: Rejected by ${secondApprover}."
            }
            // Validate that second approver is actually the designated second approver
            if (secondApprover != env.secondApproverID) {
                echo "Warning: Approval was provided by ${secondApprover}, but should be from ${env.secondApproverID}. Please try again."
                continue
            }
            // Validate at least one department selected
            if (selectedDepartments.size() == 0) {
                echo "No departments selected. Please select at least one department to deploy."
                continue
            }
            validSecondApprover = true
        } catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
            echo "Deployment process was aborted by user."
            error "Deployment aborted by user action."
        } catch (Exception e) {
            echo "Error during second approval: ${e.message}"
        }
    }
                        
    // 5. Both approvals obtained, update production image version
    echo "Deployment approved by ${firstApprover} and ${secondApprover}. Updating production image version for selected departments: ${selectedDepartments}"
    // Update only selected department tags in production chart values
    for (dept in selectedDepartments) {
        sh "sed -i 's|tag${dept}: .*|tag${dept}: ${env.imageTag}|g' chart-values/values-production.yaml"
    }
    sh "git add ."
    sh "git commit -m '[Jenkins] Updating production image version for ${selectedDepartments.join(', ')} to ${env.imageTag}'"
    withCredentials([usernamePassword(credentialsId: env.gitCredentials, passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
        sh "git push http://${GIT_USERNAME}:${GIT_PASSWORD}@${env.gitOpsRepo.replace('http://', '')}"
    }
    echo "Production deployment completed successfully."

    // 6. Healthcheck Using ArgoCD on all selected production departments
    def healthyDepartments = []
    def unhealthyDepartments = []
    def healthStatusMap = [:]
    for (dept in selectedDepartments) {
        def argoAppName = "${env.appName}-${dept}"
        sh "argocd app sync ${argoAppName}"
        def waitResult = sh(script: "argocd app wait ${argoAppName} --sync --health --timeout 900", returnStatus: true)
        def healthStatus = sh(script: "argocd app get ${argoAppName} -o json | jq -r '.status.health.status'", returnStdout: true).trim()
        healthStatusMap[dept] = healthStatus
        echo "[${dept}] Application Health Status: ${healthStatus}"
        if (waitResult == 0 && healthStatus == 'Healthy') {
            healthyDepartments << dept
        } else {
            unhealthyDepartments << dept
        }
    }
    if (unhealthyDepartments.size() > 0) {
        echo "Some departments are not healthy: ${unhealthyDepartments}. Aborting pipeline."
        def healthyList = healthyDepartments.collect {
            d -> "${d} (status: ${healthStatusMap[d]})"
        }.join(', ')
        def unhealthyList = unhealthyDepartments.collect {
            d -> "${d} (status: ${healthStatusMap[d]})"
        }.join(', ')
        sendEmailTemplate(
            MAILMODE: 'PROD_UNHEALTHY',
            RECIPIENT_EMAIL: env.authorEmail,
            RECIPIENT_NAME: env.authorName,
            EXTRA_DATA: [unhealthyList: unhealthyList, healthyList: healthyList],
            ATTACHMENT_PATTERN: 'report.html'
        )
        error "Some production departments are not healthy. Pipeline aborted."
    } else {
        echo "All selected production departments are healthy. Proceeding to next stage."
    }
}