const { CosmosClient } = require("@azure/cosmos");
const { createCart } = require("./common");

const cosmos_db_conn_string     = process.env.RECEIPTS_COSMOS_CONN_STRING || "";
const databaseId                = process.env.RECEIPT_COSMOS_DB_NAME;
const cartContainerId           = process.env.CART_COSMOS_DB_CONTAINER_NAME;
const errorCartContainerId      = process.env.CART_MESSAGE_ERRORS_COSMOS_DB_CONTAINER_NAME;


const client = new CosmosClient(cosmos_db_conn_string);
const cartContainer = client.database(databaseId).container(cartContainerId);
const errorCartContainer = client.database(databaseId).container(errorCartContainerId);


async function getDocumentByIdFromCartDatastore(id) {
    return await cartContainer.items
        .query({
            query: "SELECT * from c WHERE c.eventId=@id",
            parameters: [{ name: "@id", value: id }]
        })
        .fetchNext();
}

async function deleteDocumentFromCartsDatastoreById(id){
    let documents = await getDocumentByIdFromCartDatastore(id);
    documents?.resources?.forEach(el => {
        deleteDocumentFromCartDatastore(el.id, el.eventId);
    })
}

async function createDocumentInCartDatastore(id, eventId, status) {
    let cart = createCart(id, eventId, status);
    try {
        return await cartContainer.items.create(cart);
    } catch (err) {
        console.log(err);
        await deleteDocumentFromCartsDatastoreById(this.eventId);
        return await cartContainer.items.create(cart);
    }
}

async function deleteDocumentFromCartDatastore(id, partitionKey) {
    try {
        return await cartContainer.item(id, partitionKey).delete();
    } catch (error) {
        if (error.code !== 404) {
            console.log(error)
        }
    }
}

async function getDocumentByBizEventIdFromErrorCartDatastore(id) {
    return await errorCartContainer.items
        .query({
            query: "SELECT * from c WHERE c.id=@id",
            parameters: [{ name: "@id", value: id }]
        })
        .fetchNext();
}

async function createDocumentInErrorCartDatastore(document) {
    try {
        return await errorCartContainer.items.create(document);
    } catch (err) {
        console.log(err);
    }
}

async function deleteDocumentFromErrorCartDatastore(id) {
    try {
        return await errorCartContainer.item(id, id).delete();
    } catch (error) {
        if (error.code !== 404) {
            console.log(error)
        }
    }
}


module.exports = {
    getDocumentByIdFromCartDatastore, deleteDocumentFromCartsDatastoreById, createDocumentInCartDatastore,
    getDocumentByBizEventIdFromErrorCartDatastore, createDocumentInErrorCartDatastore, deleteDocumentFromErrorCartDatastore
}