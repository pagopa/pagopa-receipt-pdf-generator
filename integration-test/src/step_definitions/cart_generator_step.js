const assert = require('assert');
const { After, Given, When, Then, setDefaultTimeout } = require('@cucumber/cucumber');
const { sleep, createBizEventMessageForCart } = require("./common"); // Nuova helper per il messaggio Cart
// Adattamento dei client esistenti per puntare al datastore del carrello
const {
    getDocumentByIdFromCartReceiptsDatastore,
    deleteDocumentFromCartDatastoreDatastore,
    createDocumentInCartReceiptsDatastore
} = require("./cart_datastore_client");
const { putMessageOnCartReceiptQueue } = require("./cart_queue_client");
const { cartReceiptPDFExist } = require("./receipts_blob_storage_client");

// set timeout for Hooks function, it allows to wait for long task
setDefaultTimeout(360 * 1000);



// After each Scenario
After(async function() {

});

Given('a cart with id {string} and status {string} stored into cart datastore', async function(id, status) {
    this.eventId = id;
    // prior cancellation to avoid dirty cases
    await deleteDocumentFromCartDatastoreDatastore(this.eventId);

    let receiptsStoreResponse = await createDocumentInReceiptsDatastore(this.eventId, status);
    this.receiptId = this.eventId;
    assert.strictEqual(receiptsStoreResponse.statusCode, 201);
});

Given('a random biz event with id {string} enqueued on cart queue', async function(id) {
    assert.strictEqual(this.eventId, id);
    let listOfEvents = createEventsForQueue(this.eventId, null, null, STANDARD_NOTICE_NUMBER, IUV);
    await putMessageOnReceiptQueue(listOfEvents);
});

Then('the cart datastore returns the cart', async function() {
    assert.notStrictEqual(this.responseToCheck.resources.length, 0);
    this.receiptId = this.responseToCheck.resources[0].id;
    assert.strictEqual(this.responseToCheck.resources.length, 1);
});