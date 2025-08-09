import com.sap.gateway.ip.core.customdev.util.Message;
import java.util.HashMap;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import java.io.ByteArrayInputStream;

    
    def Message processData(Message message) {

        def body = message.getBody(String);
        def xml = new XmlSlurper().parseText(body)
        xml.declareNamespace(multimap: "http://sap.com/xi/XI/SplitAndMerge")

        //Body 
        def batchOutput = new StringBuilder()
        batchOutput.append("--batch\r\n")
        batchOutput.append("Content-Type: multipart/mixed; boundary=changeset_1\r\n\r\n")
        def allMessages = xml.'**'.findAll { it.name() == 'Messages' && it.namespaceURI() == "http://sap.com/xi/XI/SplitAndMerge" }

        allMessages.each { messages ->
        // Process each <multimap:Message1> and <multimap:Message2> within the current <multimap:Messages>
            def message1 = messages.'multimap:Message1'
            def message2 = messages.'multimap:Message2'

            if (message1) {
                def projectUUID = message1.'**'.find { it.name() == 'ProjectUUID' }?.text()
                def businessPartnerUUID = message2.'**'.find { it.name() == 'BusinessPartnerUUID' }?.text()

                if (projectUUID && businessPartnerUUID) {
                    // DELETE section
                    batchOutput.append("--changeset_1\r\n")
                    batchOutput.append("Content-Type: application/http\r\n")
                    batchOutput.append("Content-Transfer-Encoding: binary\r\n\r\n")
                    batchOutput.append("DELETE A_EntTeamMemberEntitlement(guid'${projectUUID}') HTTP/1.1\r\n")
                    batchOutput.append("Content-Type: application/json\r\n")
                    batchOutput.append("If-Match: *\r\n\r\n{\r\n}\r\n\r\n")

                    // POST section
                    batchOutput.append("--changeset_1\r\n")
                    batchOutput.append("Content-Type: application/http\r\n")
                    batchOutput.append("Content-Transfer-Encoding: binary\r\n\r\n")
                    batchOutput.append("POST A_EnterpriseProject(guid'${projectUUID}')/to_EntProjTeamMember HTTP/1.1\r\n")
                    batchOutput.append("Content-Type: application/json\r\n\r\n")
                    batchOutput.append("""{
                    "BusinessPartnerUUID": "${businessPartnerUUID}",
                    "to_EntProjEntitlement": [
                        {
                        "ProjectRoleType": "YP_RL_0001"
                        }
                    ]
                    }\r\n\r\n""")
                } else {
                    println "Warning: Missing ProjectUUID or BusinessPartnerUUID for a message."
                }
            }
        }

        batchOutput.append("--changeset_1--\n\n")
        batchOutput.append("--batch--")
        def batchString = batchOutput.toString()

        message.setBody(batchString);
        return message;
        }
    