import { b64encode } from 'k6/encoding';
import http from 'k6/http';

export function sendMessageToQueue(message, url, accountKey){
    let headers = {
        'x-ms-date': new Date().toUTCString()
    };

    let body = `<QueueMessage><MessageText>${b64encode(message)}</MessageText></QueueMessage>`;
    
    return http.post(`${url}?${accountKey}`, body, { headers });
}