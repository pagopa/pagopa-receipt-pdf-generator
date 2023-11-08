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