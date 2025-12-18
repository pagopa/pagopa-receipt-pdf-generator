const assert = require('assert');
const { After, Given, When, Then, setDefaultTimeout } = require('@cucumber/cucumber');
const { sleep, createEventsForQueue, createEventsForPoisonQueue, createErrorCart, createErrorReceipt, createEventsForCartQueue } = require("./common");
const { getDocumentByIdFromReceiptsDatastore, deleteDocumentFromErrorReceiptsDatastoreByBizEventId, deleteDocumentFromReceiptsDatastore, createDocumentInReceiptsDatastore, createDocumentInErrorReceiptsDatastore, deleteDocumentFromErrorReceiptsDatastore, getDocumentByBizEventIdFromErrorReceiptsDatastore } = require("./receipts_datastore_client");
const { putMessageOnPoisonQueue, putMessageOnReceiptQueue } = require("./receipts_queue_client");
const { receiptPDFExist } = require("./receipts_blob_storage_client");
const {
    getDocumentByIdFromCartDatastore, deleteDocumentFromCartsDatastoreById, createDocumentInCartDatastore,
    getDocumentByBizEventIdFromErrorCartDatastore, createDocumentInErrorCartDatastore, deleteDocumentFromErrorCartDatastore
} = require("./cart_datastore_client");
const {
    createDocumentInBizEventsDatastore,
    deleteDocumentFromBizEventsDatastore } = require("./biz_events_datastore_client");
const { postRegenerateReceiptPdf} = require("./api_helpdesk_client");
const { putMessageOnCartQueue } = require("./cart_queue_client");
const STANDARD_NOTICE_NUMBER = "310391366991197059"
const WISP_NOTICE_NUMBER = "348391366991197059"
const IUV = "10391366991197059"

// set timeout for Hooks function, it allows to wait for long task
setDefaultTimeout(360 * 1000);

// initialize variables
this.eventId = null;
this.responseToCheck = null;
this.receiptId = null;
this.errorReceiptId = null;
this.errorReceiptEventId = null;
this.listOfEvents = null;
this.transactionId = null;
this.cartId = null;
this.errorCartId = null;

// After each Scenario
After(async function () {
    // remove documents
    if (this.receiptId != null) {
        await deleteDocumentFromReceiptsDatastore(this.receiptId);
    }
    if (this.errorReceiptId != null) {
        await deleteDocumentFromErrorReceiptsDatastore(this.errorReceiptId);
    }
    if (this.errorCartId != null) {
        await deleteDocumentFromErrorCartDatastore(this.errorCartId);
    }
    if (this.transactionId != null) {
        await deleteDocumentFromCartsDatastoreById(this.transactionId);
    }
    if (this.eventId != null) {
        await deleteDocumentFromBizEventsDatastore(this.eventId);
    }


    this.eventId = null;
    this.responseToCheck = null;
    this.receiptId = null;
    this.errorReceiptId = null;
    this.errorReceiptEventId = null;
    this.listOfEvents = null;
    this.cartId = null;
    this.errorCartId = null;
    this.transactionId = null;
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
    let listOfEvents = createEventsForQueue(this.eventId, null, null, STANDARD_NOTICE_NUMBER, IUV);
    await putMessageOnReceiptQueue(listOfEvents);
});

Given('a random biz event with id {string} enqueued on receipts queue with wisp noticeCode', async function (id) {
    assert.strictEqual(this.eventId, id);
    let listOfEvents = createEventsForQueue(this.eventId, null, null, WISP_NOTICE_NUMBER, IUV);
    await putMessageOnReceiptQueue(listOfEvents);
});

Given('a random biz event with id {string} enqueued on receipts queue with wisp noticeCode and missing iuv', async function (id) {
    assert.strictEqual(this.eventId, id);
    this.errorReceiptEventId = id;
    let listOfEvents = createEventsForQueue(this.eventId, null, null, WISP_NOTICE_NUMBER, null);
    await putMessageOnReceiptQueue(listOfEvents);
});

Given("a list of {int} biz event with id {string} and transactionId {string} enqueued on receipts queue", async function (numberOfEvents, id, transactionId) {
    let listOfEvents = createEventsForQueue(id, numberOfEvents, transactionId, STANDARD_NOTICE_NUMBER, IUV);
    await putMessageOnReceiptQueue(listOfEvents);
})

Given('a list of {int} biz event with id {string} and transactionId {string} enqueued on receipts poison queue with poison retry {string}', async function (numberOfEvents, id, transactionId, value) {
    let attemptedPoisonRetry = (value === 'true');
    this.listOfEvents = createEventsForPoisonQueue(id, attemptedPoisonRetry, numberOfEvents, transactionId, STANDARD_NOTICE_NUMBER, IUV);
    this.errorReceiptEventId = transactionId;
    await deleteDocumentFromErrorReceiptsDatastoreByBizEventId(id);
    await putMessageOnPoisonQueue(this.listOfEvents);
});

When('the PDF receipt has been properly generate from biz event after {int} ms', async function (time) {
    // boundary time spent by azure function to process event
    await sleep(time);
    this.responseToCheck = await getDocumentByIdFromReceiptsDatastore(this.eventId);
});



When('the PDFs have been properly generate from cart after {int} ms', async function (time) {
    // boundary time spent by azure function to process event
    await sleep(time);
    this.responseToCheck = await getDocumentByIdFromCartDatastore(this.transactionId);
});

When('the cart is discarded from generation after {int} ms', async function (time) {
    // boundary time spent by azure function to process event
    await sleep(time);
    this.responseToCheck = await getDocumentByIdFromCartDatastore(this.transactionId);
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

Then('the receipts datastore returns the updated receipt', async function () {
    this.responseToCheck = await getDocumentByIdFromReceiptsDatastore(this.eventId);
})

Then('the blob storage has the PDF document', async function () {
    let blobExist = await receiptPDFExist(this.responseToCheck.resources[0].mdAttach.name);
    assert.strictEqual(true, blobExist);
});

Then('the blob storage has the PDF document for payer', async function () {
    let blobExist = await receiptPDFExist(this.responseToCheck.resources[0].payload.mdAttachPayer.name);
    assert.strictEqual(true, blobExist);
});


Given('a random biz event with id {string} enqueued on receipts poison queue with poison retry {string}', async function (id, value) {
    let attemptedPoisonRetry = (value === 'true');
    this.listOfEvents = createEventsForPoisonQueue(id, attemptedPoisonRetry, null, null, STANDARD_NOTICE_NUMBER, IUV);
    this.errorReceiptEventId = id;
    await deleteDocumentFromErrorReceiptsDatastoreByBizEventId(id);
    await putMessageOnPoisonQueue(this.listOfEvents);
});

When('the biz event has been properly stored on receipt-message-error datastore after {int} ms', async function (time) {
    // boundary time spent by azure function to process event
    await sleep(time);
    this.responseToCheck = await getDocumentByBizEventIdFromErrorReceiptsDatastore(this.errorReceiptEventId);
});

Then('the receipt-message-error datastore returns the error receipt', async function () {
    assert.notStrictEqual(this.responseToCheck.resources.length, 0);
    this.errorReceiptId = this.responseToCheck.resources[0].id;
    assert.strictEqual(this.responseToCheck.resources.length, 1);
});

Then('the cart-receipts-message-error datastore returns the error receipt', async function () {
    assert.notStrictEqual(this.responseToCheck.resources.length, 0);
    this.errorCartId = this.responseToCheck.resources[0].id;
    assert.strictEqual(this.responseToCheck.resources.length, 1);
});


Then('the error receipt has the status {string}', function (targetStatus) {
    assert.strictEqual(this.responseToCheck.resources[0].status, targetStatus);
});

Then('the error cart has the status {string}', function (targetStatus) {
    assert.strictEqual(this.responseToCheck.resources[0].status, targetStatus);
});

Given('a error cart with id {string} and transactionId {string} stored into cart-receipts-message-error datastore with status REVIEWED', async function (cartId, transactionId) {
    await deleteDocumentFromErrorCartDatastore(cartId);

    assert.strictEqual(this.transactionId, transactionId);
    let response = await createDocumentInErrorCartDatastore(createErrorCart(cartId, transactionId));
    this.errorCartId = cartId;
    assert.strictEqual(response.statusCode, 201);
});

Given('a error receipt with id {string} stored into receipt-message-error datastore with status REVIEWED', async function (id) {
    await deleteDocumentFromErrorReceiptsDatastore(id);

    assert.strictEqual(this.eventId, id);
    let response = await createDocumentInErrorReceiptsDatastore(createErrorReceipt(id));
    this.errorReceiptId = id;
    assert.strictEqual(response.statusCode, 201);
});

When('the error cart has been properly stored on cart-receipts-message-error datastore after {int} ms', async function (time) {
    // boundary time spent by azure function to process event
    await sleep(time);
    this.responseToCheck = await getDocumentByBizEventIdFromErrorCartDatastore(this.errorCartId);
});

When('the error receipt has been properly stored on receipt-message-error datastore after {int} ms', async function (time) {
    // boundary time spent by azure function to process event
    await sleep(time);
    this.responseToCheck = await getDocumentByBizEventIdFromErrorReceiptsDatastore(this.errorReceiptId);
});

Given('a cart with id {string} and eventId {string} and status {string} stored into cart datastore', async function (cartId, eventId, status) {
    this.cartId = cartId;
    this.transactionId = eventId;

    // prior cancellation to avoid dirty cases
    await deleteDocumentFromCartsDatastoreById(eventId);

    let cartStoreResponse = await createDocumentInCartDatastore(cartId, eventId, status);
    assert.strictEqual(cartStoreResponse.statusCode, 201);
});

Given('random biz events for cart with id {string} and transaction id {string} enqueued on cart queue', async function (id, transactionId) {
    assert.strictEqual(this.transactionId, transactionId);
    let listOfEvents = createEventsForCartQueue(id, 2, transactionId, STANDARD_NOTICE_NUMBER, IUV);
    await putMessageOnCartQueue(listOfEvents);
});

Then('the cart datastore returns the cart', async function () {
    assert.notStrictEqual(this.responseToCheck.resources.length, 0);
    this.cartId = this.responseToCheck.resources[0].id;
    assert.strictEqual(this.responseToCheck.resources.length, 1);
});

/*
    Helpdesk
*/
Given('a biz event with id {string} and status {string} stored on biz-events datastore', async function (id, status) {
    this.eventId = id;
    // prior cancellation to avoid dirty cases
    await deleteDocumentFromBizEventsDatastore(this.eventId);

    let bizEventStoreResponse = await createDocumentInBizEventsDatastore(this.eventId, status);
    assert.strictEqual(bizEventStoreResponse.statusCode, 201);
});

When('regenerateReceiptPdf API is called with bizEventId {string} as query param', async function (id) {
    responseAPI = await postRegenerateReceiptPdf(id);
});

Then('the api response has a {int} Http status', function (expectedStatus) {
    assert.strictEqual(responseAPI.status, expectedStatus);
});

Then("the receipt with eventId {string} is recovered from datastore", async function (id) {
    let responseCosmos = await getDocumentByIdFromReceiptsDatastore(id);
    assert.strictEqual(responseCosmos.resources.length > 0, true);
    receipt = responseCosmos.resources[0];
});

Then('the receipt has attachment metadata', function () {
    assert.strictEqual(receipt.mdAttach != undefined && receipt.mdAttach != null && receipt.mdAttach.name != "", true);
    receiptPdfFileName = receipt.mdAttach.name;
    this.receiptId = receipt.id;
});

Then('the PDF is present on blob storage', async function () {
    let blobExist = await receiptPDFExist(receiptPdfFileName);
    assert.strictEqual(blobExist, true);
  });