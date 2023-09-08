import http from 'k6/http';
import crypto from 'k6/crypto';

export function sendMessageToQueue(message, url, accountName, accountKey, queueName){
    let headers = getAPIHeaders(accountName, accountKey, "POST", queueName);

    let body = `<QueueMessage><MessageText>${message}</MessageText></QueueMessage>`;
    
    return http.post(url, body, { headers });
}

function getAPIHeaders(accountName, accountKey, httpMethod, queueName){

    return {
        'Authorization': generateAPISharedKey(accountName, accountKey, httpMethod, queueName),
        'x-ms-date': new Date().toUTCString()
    };
}

function generateAPISharedKey(accountName, accountKey, httpMethod, queueName){
    const resourcePath = `/${queueName}`;
    
    // Crea la stringa da firmare
    const date = new Date().toUTCString();
    const stringToSign = httpMethod + "\n" +  
     "\n" +  
    "\n" +  
     "\n" +  
     "\n" +  
     "\n" +  
     "\n" +  
     "\n" +  
     "\n" +  
     "\n" +  
     "\n" +  
    `nx-ms-date:${date}` + "\n" +
    resourcePath;
    
    // Calcola la firma HMAC-SHA256
    const signature = crypto.hmac("sha256", accountKey, encodeURI(stringToSign), "hex");

    console.log(encodeURI(stringToSign));
    
    // Crea l'intestazione "Authorization"
    return `SharedKey ${accountName}:${signature}`;
}