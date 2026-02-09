const { CosmosClient } = require("@azure/cosmos");

// https://learn.microsoft.com/en-us/azure/cosmos-db/nosql/tutorial-global-distribution?tabs=dotnetv2%2Capi-async#nodejsjavascript
// Setting read region selection preference, in the following order -
// 1 - West Europe
// 2 - North Europe
// const preferredLocations_ = ['West Europe', 'North Europe'];
const preferredLocations = ["North Europe"];

function createClient(endpoint, key, timeout) {
    console.log("endpoint", endpoint);
    console.log("key", key);
    return new CosmosClient({
        endpoint,
        key,
        connectionPolicy: {
            requestTimeout: timeout,
            preferredLocations,
        },
    });
}

/* =======================
 * Receipt Cosmos
 * ======================= */

const receiptClient = createClient(
    process.env.RECEIPTS_COSMOS_ENDPOINT,
    process.env.RECEIPTS_COSMOS_KEY,
    process.env.RECEIPTS_COSMOS_TIMEOUT || 10000
);

const receiptDatabase = receiptClient.database(
    process.env.RECEIPT_COSMOS_DB_NAME
);

const receiptContainer = receiptDatabase.container(
    process.env.RECEIPT_COSMOS_DB_CONTAINER_NAME
);

const cartReceiptContainer = receiptDatabase.container(
    process.env.RECEIPT_COSMOS_DB_CART_CONTAINER_NAME
);

/* =======================
 * Biz Cosmos
 * ======================= */

const bizClient = createClient(
    process.env.BIZ_COSMOS_ENDPOINT,
    process.env.BIZ_COSMOS_KEY,
    process.env.RECEIPTS_COSMOS_TIMEOUT || 10000
);

const bizContainer = bizClient
    .database(process.env.BIZ_COSMOS_DB_NAME)
    .container(process.env.BIZ_COSMOS_DB_CONTAINER_NAME);


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

async function getCartReceiptsStatusCount(data_from, data_to) {
    return await cartReceiptContainer.items
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
    getReceiptsStatusCount, getCartReceiptsStatusCount, getBizCount
}
