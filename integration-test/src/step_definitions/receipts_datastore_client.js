const { CosmosClient } = require("@azure/cosmos");
const { createReceipt } = require("./common");

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
        deleteDocumentFromReceiptsDatastore(el.id);
    })
}

async function createDocumentInReceiptsDatastore(id, status) {
    let receipt = createReceipt(id, status);
    try {
        return await receiptContainer.items.create(receipt);
    } catch (err) {
        console.log(err);
    }
}

async function deleteDocumentFromReceiptsDatastore(id) {
    try {
        return await receiptContainer.item(id, id).delete();
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

async function createDocumentInErrorReceiptsDatastore(document) {
    try {
        return await errorReceiptContainer.items.create(document);
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