const { CosmosClient } = require("@azure/cosmos");
const { createReceipt, createEventForPoisonQueue } = require("./common");

const TOKENIZED_BIZ_EVENT ='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';

const cosmos_db_conn_string     = process.env.RECEIPTS_COSMOS_CONN_STRING || "";
const databaseId                = process.env.RECEIPT_COSMOS_DB_NAME;
const receiptContainerId        = process.env.RECEIPT_COSMOS_DB_CONTAINER_NAME;
const errorReceiptContainerId   = process.env.RECEIPT_MESSAGE_ERRORS_COSMOS_DB_CONTAINER_NAME;

const client = new CosmosClient(cosmos_db_conn_string);
const receiptContainer = client.database(databaseId).container(receiptContainerId);
const errorReceiptContainer = client.database(databaseId).container(errorReceiptContainerId);

async function getDocumentByIdFromReceiptsDatastore(id) {
    return await receiptContainer.items
        .query({
            query: "SELECT * from c WHERE c.eventId=@eventId",
            parameters: [{ name: "@eventId", value: id }]
        })
        .fetchNext();
}

async function deleteDocumentFromReceiptsDatastoreByEventId(eventId){
    let documents = await getDocumentByIdFromReceiptsDatastore(eventId);

    documents?.resources?.forEach(el => {
        deleteDocumentFromReceiptsDatastore(el.id, eventId);
    })
}

async function createDocumentInReceiptsDatastore(id) {
    let event = createReceipt(id);
    try {
        return await receiptContainer.items.create(event);
    } catch (err) {
        console.log(err);
    }
}

async function deleteDocumentFromReceiptsDatastore(id, partitionKey) {
    try {
        return await receiptContainer.item(id, partitionKey).delete();
    } catch (error) {
        if (error.code !== 404) {
            console.log(error)
        }
    }
}

// receipts-message-error datastore

async function getDocumentByBizEventIdFromErrorReceiptsDatastore(bizEventId) {
    return await errorReceiptContainer.items
        .query({
            query: "SELECT * from c WHERE c.bizEventId=@bizEventId",
            parameters: [{ name: "@bizEventId", value: bizEventId }]
        })
        .fetchNext();
}

async function createDocumentInErrorReceiptsDatastore(id) {
    let payload = {
        "messagePayload": TOKENIZED_BIZ_EVENT,
        "bizEventId": id,
        "status": "REVIEWED",
        "id": id,
        "_rid": "Z9AJAJpW0pIhAAAAAAAAAA==",
        "_self": "dbs/Z9AJAA==/colls/Z9AJAJpW0pI=/docs/Z9AJAJpW0pIhAAAAAAAAAA==/",
        "_etag": "\"7e005a10-0000-0d00-0000-64a27a780000\"",
        "_attachments": "attachments/",
        "_ts": 1688369784
    };
    try {
        return await errorReceiptContainer.items.create(payload);
    } catch (err) {
        console.log(err);
    }
}

async function deleteDocumentFromErrorReceiptsDatastore(id) {
    try {
        return await errorReceiptContainer.item(id, id).delete();
    } catch (error) {
        if (error.code !== 404) {
            console.log(error)
        }
    }
}

async function deleteDocumentFromErrorReceiptsDatastoreByBizEventId(bizEventId) {
    let documents = await getDocumentByBizEventIdFromErrorReceiptsDatastore(bizEventId);

    documents?.resources?.forEach((el) => {
        deleteDocumentFromErrorReceiptsDatastore(el.id);
    })
}

module.exports = {
    getDocumentByIdFromReceiptsDatastore, deleteDocumentFromReceiptsDatastoreByEventId, createDocumentInReceiptsDatastore, deleteDocumentFromReceiptsDatastore, getDocumentByBizEventIdFromErrorReceiptsDatastore, createDocumentInErrorReceiptsDatastore, deleteDocumentFromErrorReceiptsDatastore, deleteDocumentFromErrorReceiptsDatastoreByBizEventId
}