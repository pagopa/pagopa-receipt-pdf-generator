const { QueueServiceClient } = require("@azure/storage-queue");

const connStr                  = process.env.RECEIPTS_STORAGE_CONN_STRING || "";
const cartQueueName            = process.env.CART_QUEUE_NAME;
const poisonQueueName          = process.env.CART_POISON_QUEUE_NAME;

const queueServiceClient       = QueueServiceClient.fromConnectionString(connStr);
const cartQueueClient       = queueServiceClient.getQueueClient(cartQueueName);
const errorCartQueueClient  = queueServiceClient.getQueueClient(poisonQueueName);

async function putMessageOnCartQueue(message) {
    // Send a message into the queue using the sendMessage method.
    message = btoa(JSON.stringify(message));
    return await cartQueueClient.sendMessage(message);
}

async function putMessageOnCartPoisonQueue(message) {
    // Send a message into the queue using the sendMessage method.
    message = btoa(JSON.stringify(message));
    return await errorCartQueueClient.sendMessage(message);
}

module.exports = {
    putMessageOnCartQueue, putMessageOnCartPoisonQueue
}