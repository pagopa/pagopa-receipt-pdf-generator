const { CosmosClient, ConnectionPolicy } = require("@azure/cosmos");
const { get } = require("http");

// receipt
const receipt_cosmos_endpoint = process.env.RECEIPTS_COSMOS_ENDPOINT || "";
const receipt_cosmos_key = process.env.RECEIPTS_COSMOS_KEY || "";
const databaseId = process.env.RECEIPT_COSMOS_DB_NAME;
const receiptContainerId = process.env.RECEIPT_COSMOS_DB_CONTAINER_NAME;
request_timeout = process.env.RECEIPTS_COSMOS_TIMEOUT || 10000;
const client = new CosmosClient({
   endpoint: receipt_cosmos_endpoint,
   key: receipt_cosmos_key,
   connectionPolicy: {
      requestTimeout: request_timeout
   }
});
const receiptContainer = client.database(databaseId).container(receiptContainerId);

//biz
const biz_cosmos_endpoint = process.env.BIZ_COSMOS_ENDPOINT || "";
const biz_cosmos_key = process.env.BIZ_COSMOS_KEY || "";
const biz_databaseId = process.env.BIZ_COSMOS_DB_NAME;
const bizContainerId = process.env.BIZ_COSMOS_DB_CONTAINER_NAME;
const biz_request_timeout = process.env.RECEIPTS_COSMOS_TIMEOUT || 10000;

// https://learn.microsoft.com/en-us/azure/cosmos-db/nosql/tutorial-global-distribution?tabs=dotnetv2%2Capi-async#nodejsjavascript
// Setting read region selection preference, in the following order -
// 1 - West Europe
// 2 - North Europe
// const preferredLocations_ = ['West Europe', 'North Europe'];
const preferredLocations_ = ['North Europe'];

const biz_client = new CosmosClient({
   endpoint: biz_cosmos_endpoint,
   key: biz_cosmos_key,
      connectionPolicy: {
         requestTimeout: biz_request_timeout,
         preferredLocations :  preferredLocations_
      }
});
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
