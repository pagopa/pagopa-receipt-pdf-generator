const { CosmosClient } = require("@azure/cosmos");
const { createReceipt, createEventForPoisonQueue } = require("./common");

const TOKENIZED_BIZ_EVENT = "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";

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