import { sleep, check } from 'k6';
import { SharedArray } from 'k6/data';

import { randomString, createEvent, createReceipt } from './modules/common.js'
import { createDocument, deleteDocument, getDocumentByEventId } from "./modules/datastore_client.js";
import {sendMessageToQueue} from "./modules/queue_client.js";

export let options = JSON.parse(open(__ENV.TEST_TYPE));

// read configuration
// note: SharedArray can currently only be constructed inside init code
// according to https://k6.io/docs/javascript-api/k6-data/sharedarray
const varsArray = new SharedArray('vars', function () {
	return JSON.parse(open(`./${__ENV.VARS}`)).environment;
});
// workaround to use shared array (only array should be used)
const vars = varsArray[0];
//COSMOS
const receiptCosmosDBURI = `${vars.receiptCosmosDBURI}`;
const receiptDatabaseID = `${vars.receiptDatabaseID}`;
const receiptContainerID = `${vars.receiptContainerID}`;
const receiptCosmosDBPrimaryKey = `${__ENV.RECEIPT_COSMOS_DB_SUBSCRIPTION_KEY}`;
//QUEUE
const receiptQueueAccountName = `${vars.receiptQueueAccountName}`;
const receiptQueueName = `${vars.receiptQueueName}`;
const receiptQueueURI = `https://${receiptQueueAccountName}.queue.core.windows.net/${receiptQueueName}/messages`;
const receiptQueuePrimaryKey = `${__ENV.RECEIPT_QUEUE_SUBSCRIPTION_KEY}`;
// boundary time (s) to process event: activate trigger, process function, upload event to datastore
const processTime = `${vars.processTime}`;

export function setup() {
	// 2. setup code (once)
	// The setup code runs, setting up the test environment (optional) and generating data
	// used to reuse code for the same VU

	// todo

	// precondition is moved to default fn because in this stage
	// __VU is always 0 and cannot be used to create env properly
}

// teardown the test data
export function teardown(data) {
	// todo
}

function postcondition(eventId) {
	// verify that published event have been stored properly in the datastore
	let tag = { datastoreMethod: "GetDocumentByEventId" };
	let r = getDocumentByEventId(receiptCosmosDBURI, receiptDatabaseID, receiptContainerID, receiptCosmosDBPrimaryKey, eventId);

	console.log("GetDocumentByEventId call, Status " + r.status);

	let { _count, Documents } = r.json();

	let receipt = Documents[0];

	check(r, {
		"Assert published receipt is in the datastore and with status GENERATED or beyond": (_r) => _count === 1 && (
			receipt.status !== "INSERTED" &&
			receipt.status !== "FAILED" &&
			receipt.status !== "NOT_QUEUE_SENT" &&
			receipt.status !== "RETRY"
		) &&
			receipt.mdAttach
			&&
			receipt.mdAttach.url
		,
	}, tag);

	if (_count) {
		let receiptId = receipt.id;

		deleteDocument(receiptCosmosDBURI, receiptDatabaseID, receiptContainerID, receiptCosmosDBPrimaryKey, receiptId);
	}
}

export default function () {
	// publish receipt
	let tag = { datastoreMethod: "SaveReceipt" };
	const id = randomString(15, "abcdefghijklmnopqrstuvwxyz0123456789");

	let receipt = createReceipt(id);

	let responseReceipt = createDocument(receiptCosmosDBURI, receiptDatabaseID, receiptContainerID, receiptCosmosDBPrimaryKey, receipt, id);

	console.log("PublishReceipt call, Status " + responseReceipt.status);

	check(responseReceipt, {
		'PublishReceipt status is 201': (_response) => responseReceipt.status === 201,
	}, tag);

	//if the receipt is saved
	if (responseReceipt.status === 201) {
		//send event to queue
		tag = { storageMethod: "SendMessageToQueue" };

		let eventMessage = JSON.stringify(createEvent(id));
		let responseQueue = sendMessageToQueue(eventMessage, receiptQueueURI, receiptQueuePrimaryKey);

		console.log("SendMessageToQueue call, Status " + responseQueue.status);
	
		check(eventMessage, {
			'SendMessageToQueue status is 201': (_response) => responseQueue.status === 201,
		}, tag);
	
		//if the message is sent to queue wait and check if it was correctly processed
		if (responseQueue.status === 201) {
			sleep(processTime);
			postcondition(id);
		}
	}
}
