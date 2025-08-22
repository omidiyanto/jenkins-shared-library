def call(Map config = [:]) {
    emailext(
        attachLog: true,
        subject: "${config.JENKINS_PIPELINE_STATUS} Jenkins Pipeline - ${env.JOB_NAME}#${env.BUILD_NUMBER}",
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