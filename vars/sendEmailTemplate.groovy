def mail(Map config = [:]) {
    if (config.MAILMODE == "START-AUTOMATED") {
        SUBJECT= "[START] Jenkins Pipeline - ${env.JOB_NAME}#${env.BUILD_NUMBER}"
        MAILMESSAGE = """
Your recent commit (${env.commit_sha}) has triggered a new build of ${env.JOB_NAME} #${env.BUILD_NUMBER}.

Commit Message: 
${env.commitMessage}
"""
    } else if (config.MAILMODE == "START-MANUAL") {
        SUBJECT= "[START] Jenkins Pipeline - ${env.JOB_NAME}#${env.BUILD_NUMBER}"
        MAILMESSAGE = "Your recent manual build of ${env.JOB_NAME} #${env.BUILD_NUMBER} has been started."
    }

    
    emailext(
        attachLog: true,
        subject: SUBJECT,
        body: """ 
Hello ${config.RECIPIENT}, 

${config.MAILMESSAGE}

You can follow the progress here:
${env.JENKINS_URL}blue/organizations/jenkins/${env.encodedJob}/detail/${env.jobDisplay}/${env.BUILD_NUMBER}/pipeline

Thank you.

Best regards,  
DevOps Team
""",
        to: """\
            ${config.RECIPIENTEMAIL},
            cc:${env.developersEmail},
            bcc:${env.bccEmail1},
            bcc:${env.bccEmail2}
        """
    )
}

def argocdUATUnhealthy(Map config = [:]) {
    emailext(
        attachLog: true,
        subject: "${config.JENKINS_PIPELINE_STATUS} Jenkins Pipeline - ${env.JOB_NAME}#${env.BUILD_NUMBER}",
        body: """ 
Hello Team,

The CI/CD pipeline for job ${env.JOB_NAME} with build number #${env.BUILD_NUMBER} has FAILED because the ArgoCD application '${env.argoAppName}' is not healthy (status: ${healthStatus}) or timed out during sync/wait.

Please check the ArgoCD dashboard and application logs for more details, and check the pipeline logs via the Blue Ocean link below: 
${env.JENKINS_URL}blue/organizations/jenkins/${env.encodedJob}/detail/${env.jobDisplay}/${env.BUILD_NUMBER}/pipeline

Thank you.
 
Best regards,  
DevSecOps Team
""",
        attachmentsPattern: 'report.html',
        to: """\
            ${env.authorEmail},
            cc:${env.developersEmail},
            bcc:${env.bccEmail1},
            bcc:${env.bccEmail2}
        """
    )
}

def approval(Map config = [:]) {
    emailext(
        attachLog: true,
        subject: "Production Deployment - Approver 1 Notification - ${env.JOB_NAME}#${env.BUILD_NUMBER}",
        body: """
Dear ${env.firstApproverID},

A deployment to the Production environment has been initiated for the following pipeline:

Job Name   : ${env.JOB_NAME}  
Build No.  : ${env.BUILD_NUMBER}

The pipeline is waiting for your approval to proceed with the deployment.

Please access the Jenkins pipeline via the following Blue Ocean link to proceed with approval:  
${env.JENKINS_URL}blue/organizations/jenkins/${env.encodedJob}/detail/${env.jobDisplay}/${env.BUILD_NUMBER}/pipeline

Your prompt attention and decision is highly appreciated to proceed with the production release.

Best regards,  
DevSecOps Team
""",
        attachmentsPattern: 'report.html',
        to: """\
            ${env.firstApproverEmail},
            bcc:${env.bccEmail1},
            bcc:${env.bccEmail2}
        """
    )
}