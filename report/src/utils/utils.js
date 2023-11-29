const { CosmosClient } = require("@azure/cosmos");
const { get } = require("http");

const cosmos_db_conn_string = process.env.RECEIPTS_COSMOS_CONN_STRING || "";
const databaseId = process.env.RECEIPT_COSMOS_DB_NAME;
const receiptContainerId = process.env.RECEIPT_COSMOS_DB_CONTAINER_NAME;
const errorReceiptContainerId = process.env.RECEIPT_MESSAGE_ERRORS_COSMOS_DB_CONTAINER_NAME;

const client = new CosmosClient(cosmos_db_conn_string);
const receiptContainer = client.database(databaseId).container(receiptContainerId);
// const errorReceiptContainer = client.database(databaseId).container(errorReceiptContainerId);


async function getReceiptsStatusCount(data_from, data_to) {
    return await receiptContainer.items
        .query({
            query: `SELECT count(1) as num,c.status FROM c WHERE
            c.inserted_at >= DateTimeToTimestamp(@datefrom)
            and c.inserted_at < DateTimeToTimestamp(@dateto)
            GROUP BY c.status`,
            parameters: [
                { name: "@datefrom", value: data_from },
                { name: "@dateto", value: data_to },
            ]
        })
        .fetchAll();
}

module.exports = {
    getReceiptsStatusCount
}