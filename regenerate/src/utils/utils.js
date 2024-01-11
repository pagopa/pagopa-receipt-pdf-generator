const { CosmosClient } = require("@azure/cosmos");
const { get } = require("http");

// receipt
const cosmos_db_conn_string = process.env.RECEIPTS_COSMOS_CONN_STRING || "";
const databaseId = process.env.RECEIPT_COSMOS_DB_NAME;
const receiptContainerId = process.env.RECEIPT_COSMOS_DB_CONTAINER_NAME;
const client = new CosmosClient(cosmos_db_conn_string);
const receiptContainer = client.database(databaseId).container(receiptContainerId);


async function getReceiptsToProcess() {
    return await receiptContainer.items
        .query({
            query: `SELECT c.eventId FROM c WHERE
                      c.inserted_at >= DateTimeToTimestamp("2024-01-08T00:00:00")
                      and c.inserted_at <= DateTimeToTimestamp("2024-08-10T23:59:59")
                      and c.status = "IO_NOTIFIED"
                      and not CONTAINS(c.eventData.amount, ".", false)`,
            parameters: []
        })
        .fetchAll();
}


const axios = require("axios");

axios.defaults.headers.common['Ocp-Apim-Subscription-Key'] = process.env.SUBKEY || "";// for all requests
if (process.env.CANARY) {
  axios.defaults.headers.common['X-Canary'] = 'canary' // for all requests
}

function post(url, body, headers) {
    return axios.post(url, body, {headers})
        .then(res => {
            return res;
        })
        .catch(error => {
	console.log(error)
            return error.response;
        });
}

module.exports = {
    getReceiptsToProcess, post
}
