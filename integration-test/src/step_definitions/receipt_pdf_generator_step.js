const assert = require('assert');
const { After, Given, When, Then, setDefaultTimeout } = require('@cucumber/cucumber');
const { sleep, createEventForQueue, createEventForPoisonQueue } = require("./common");
const { getDocumentByIdFromReceiptsDatastore, deleteDocumentFromErrorReceiptsDatastoreByBizEventId, deleteDocumentFromReceiptsDatastore, createDocumentInReceiptsDatastore, createDocumentInErrorReceiptsDatastore, deleteDocumentFromErrorReceiptsDatastore, getDocumentByBizEventIdFromErrorReceiptsDatastore } = require("./receipts_datastore_client");
const { putMessageOnPoisonQueue, putMessageOnReceiptQueue } = require("./receipts_queue_client");
const { receiptPDFExist } = require("./receipts_blob_storage_client");

// set timeout for Hooks function, it allows to wait for long task
setDefaultTimeout(360 * 1000);

// initialize variables
this.eventId = null;
this.responseToCheck = null;
this.receiptId = null;
this.errorReceiptId = null;
this.event = null;

// After each Scenario
After(async function () {
    // remove documents
    if (this.receiptId != null) {
        await deleteDocumentFromReceiptsDatastore(this.receiptId);
    }
    if (this.errorReceiptId != null) {
        await deleteDocumentFromErrorReceiptsDatastore(this.errorReceiptId);
    }
    this.eventId = null;
    this.responseToCheck = null;
    this.receiptId = null;
    this.errorReceiptId = null;
    this.event = null;
});


Given('a receipt with id {string} and status {string} stored into receipt datastore', async function (id, status) {
    this.eventId = id;
    // prior cancellation to avoid dirty cases
    await deleteDocumentFromReceiptsDatastore(this.eventId);

    let receiptsStoreResponse = await createDocumentInReceiptsDatastore(this.eventId, status);
    this.receiptId = this.eventId;
    assert.strictEqual(receiptsStoreResponse.statusCode, 201);
});

Given('a random biz event with id {string} enqueued on receipts queue', async function (id) {
    assert.strictEqual(this.eventId, id);
    let event = createEventForQueue(this.eventId);
    await putMessageOnReceiptQueue(event);
});


When('the PDF receipt has been properly generate from biz event after {int} ms', async function (time) {
    // boundary time spent by azure function to process event
    await sleep(time);
    this.responseToCheck = await getDocumentByIdFromReceiptsDatastore(this.eventId);
});

Then('the receipts datastore returns the receipt', async function () {
    assert.notStrictEqual(this.responseToCheck.resources.length, 0);
    this.receiptId = this.responseToCheck.resources[0].id;
    assert.strictEqual(this.responseToCheck.resources.length, 1);
});

Then('the receipt has eventId {string}', function (targetId) {
    assert.strictEqual(this.responseToCheck.resources[0].eventId, targetId);
});

Then('the receipt has not the status {string}', function (targetStatus) {
    assert.notStrictEqual(this.responseToCheck.resources[0].status, targetStatus);
});

Then('the receipt has the status {string}', function (targetStatus) {
    assert.strictEqual(this.responseToCheck.resources[0].status, targetStatus);
});

Then('the receipts datastore returns the updated receipt', async function(){
    this.responseToCheck = await getDocumentByIdFromReceiptsDatastore(this.eventId);
})

Then('the blob storage has the PDF document', async function () {
    let blobExist = await receiptPDFExist(this.responseToCheck.resources[0].mdAttach.name);
    assert.strictEqual(true, blobExist);
});


Given('a random biz event with id {string} enqueued on receipts poison queue with poison retry {string}', async function (id, value) {
    let attemptedPoisonRetry = (value === 'true');
    this.event = createEventForPoisonQueue(id, attemptedPoisonRetry);
    await deleteDocumentFromErrorReceiptsDatastoreByBizEventId(id);
    await putMessageOnPoisonQueue(this.event);
});

When('the biz event has been properly stored on receipt-message-error datastore after {int} ms', async function (time) {
    // boundary time spent by azure function to process event
    await sleep(time);
    this.responseToCheck = await getDocumentByBizEventIdFromErrorReceiptsDatastore(this.event.id);
});

Then('the receipt-message-error datastore returns the error receipt', async function () {
    assert.notStrictEqual(this.responseToCheck.resources.length, 0);
    this.errorReceiptId = this.responseToCheck.resources[0].id;
    assert.strictEqual(this.responseToCheck.resources.length, 1);
});

Then('the error receipt has the status {string}', function (targetStatus) {
    assert.strictEqual(this.responseToCheck.resources[0].status, targetStatus);
});

Given('a error receipt with id {string} stored into receipt-message-error datastore with status REVIEWED', async function (id) {
    await deleteDocumentFromErrorReceiptsDatastore(id);

    assert.strictEqual(this.eventId, id);
    let response = await createDocumentInErrorReceiptsDatastore(id);
    this.errorReceiptId = id;
    assert.strictEqual(response.statusCode, 201);
});