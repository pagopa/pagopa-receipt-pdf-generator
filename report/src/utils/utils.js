const { CosmosClient } = require("@azure/cosmos");
const { get } = require("http");

// receipt
const cosmos_db_conn_string = process.env.RECEIPTS_COSMOS_CONN_STRING || "";
const databaseId = process.env.RECEIPT_COSMOS_DB_NAME;
const receiptContainerId = process.env.RECEIPT_COSMOS_DB_CONTAINER_NAME;
const client = new CosmosClient(cosmos_db_conn_string);
const receiptContainer = client.database(databaseId).container(receiptContainerId);

//biz
const biz_cosmos_db_conn_string = process.env.BIZ_COSMOS_CONN_STRING || "";
const biz_databaseId = process.env.BIZ_COSMOS_DB_NAME;
const bizContainerId = process.env.BIZ_COSMOS_DB_CONTAINER_NAME;
const biz_client = new CosmosClient(biz_cosmos_db_conn_string);
const bizContainer = biz_client.database(biz_databaseId).container(bizContainerId);


async function getReceiptsStatusCount(data_from, data_to) {
    return await receiptContainer.items
        .query({
            query: `SELECT count(1) as num,c.status FROM c WHERE
            c.inserted_at >= DateTimeToTimestamp(@datefrom)
            and c.inserted_at <= DateTimeToTimestamp(@dateto)
            GROUP BY c.status`,
            parameters: [
                { name: "@datefrom", value: data_from },
                { name: "@dateto", value: data_to },
            ]
        })
        .fetchAll();
}


async function getBizCount(data_from, data_to) {
    return await bizContainer.items
        .query({
            query: `SELECT count(1) as num  FROM c WHERE c.eventStatus = "DONE"
            and c.timestamp >= DateTimeToTimestamp(@datefrom)
            and c.timestamp <= DateTimeToTimestamp(@dateto)`,
            parameters: [
                { name: "@datefrom", value: data_from },
                { name: "@dateto", value: data_to },
            ]
        })
        .fetchAll();
}

module.exports = {
    getReceiptsStatusCount, getBizCount
}
