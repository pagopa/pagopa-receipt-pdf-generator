const { CosmosClient } = require("@azure/cosmos");
const { createCart } = require("./common");

const cosmos_db_conn_string     = process.env.RECEIPTS_COSMOS_CONN_STRING || "";
const databaseId                = process.env.RECEIPT_COSMOS_DB_NAME;
const cartContainerId           = process.env.CART_COSMOS_DB_CONTAINER_NAME;

const client = new CosmosClient(cosmos_db_conn_string);
const cartContainer = client.database(databaseId).container(cartContainerId);

async function getDocumentByIdFromCartDatastore(id) {
    return await cartContainer.items
        .query({
            query: "SELECT * from c WHERE c.id=@id",
            parameters: [{ name: "@id", value: id }]
        })
        .fetchNext();
}

async function deleteDocumentFromCartsDatastoreById(id){
    let documents = await getDocumentByIdFromCartDatastore(id);

    documents?.resources?.forEach(el => {
        deleteDocumentFromCartDatastore(el.id);
    })
}

async function createDocumentInCartDatastore(id, status) {
    let cart = createCart(id, status);
    try {
        return await cartContainer.items.create(cart);
    } catch (err) {
        console.log(err);
    }
}

async function deleteDocumentFromCartDatastore(id) {
    try {
        return await cartContainer.item(id, id).delete();
    } catch (error) {
        if (error.code !== 404) {
            console.log(error)
        }
    }
}




module.exports = {
    getDocumentByIdFromCartDatastore, deleteDocumentFromCartsDatastoreById, createDocumentInCartDatastore
}