const FISCAL_CODE = "AAAAAA00A00A000A";
const TOKENIZED_FISCAL_CODE = "cd07268c-73e8-4df4-8305-a35085e32eff";
const {encryptText } = require("./aesUtils");

function sleep(ms) {
	return new Promise(resolve => setTimeout(resolve, ms));
}

function createEventsForQueue(id, numberOfEvents, transactionId, noticeNumber, iuv) {
	return createEventsForPoisonQueue(id, false, numberOfEvents, transactionId, noticeNumber, iuv);
}

function createEventsForPoisonQueue(id, attemptedPoisonRetry, numberOfEvents, transactionId, noticeNumber, iuv) {
	let arrayOfEvents = [];
	for(let i = 0; i < (numberOfEvents ?? 1); i++) {
		let finalId = id+(i != 0 ? i : "");
		let json_event = {
			"id": finalId,
			"version": "2",
			"idPaymentManager": "54927408",
			"complete": "false",
			"receiptId": "9851395f09544a04b288202299193ca6",
			"attemptedPoisonRetry": attemptedPoisonRetry,
			"missingInfo": [
				"psp.pspPartitaIVA",
				"paymentInfo.primaryCiIncurredFee",
				"paymentInfo.idBundle",
				"paymentInfo.idCiBundle"
			],
			"debtorPosition": {
				"modelType": "2",
				"noticeNumber": noticeNumber,
				"iuv": iuv
			},
			"creditor": {
				"idPA": "66666666666",
				"idBrokerPA": "66666666666",
				"idStation": "66666666666_08",
				"companyName": "PA paolo",
				"officeName": "office"
			},
			"psp": {
				"idPsp": "BNLIITRR",
				"idBrokerPsp": "60000000001",
				"idChannel": "60000000001_08",
				"psp": "PSP Paolo",
				"pspFiscalCode": "CF60000000006",
				"channelDescription": "app"
			},
			"debtor": {
				"fullName": "paGetPaymentName",
				"entityUniqueIdentifierType": "G",
				"entityUniqueIdentifierValue": FISCAL_CODE,
				"streetName": "paGetPaymentStreet",
				"civicNumber": "paGetPayment99",
				"postalCode": "20155",
				"city": "paGetPaymentCity",
				"stateProvinceRegion": "paGetPaymentState",
				"country": "IT",
				"eMail": "paGetPayment@test.it"
			},
			"payer": {
				"fullName": "name",
				"entityUniqueIdentifierType": "G",
				"entityUniqueIdentifierValue": FISCAL_CODE,
				"streetName": "street",
				"civicNumber": "civic",
				"postalCode": "postal",
				"city": "city",
				"stateProvinceRegion": "state",
				"country": "IT",
				"eMail": "prova@test.it"
			},
			"paymentInfo": {
				"paymentDateTime": "2023-03-17T16:37:36.955813",
				"applicationDate": "2021-12-12",
				"transferDate": "2021-12-11",
				"dueDate": "2021-12-12",
				"paymentToken": "9851395f09544a04b288202299193ca6",
				"amount": "10.0",
				"fee": "2.0",
				"totalNotice": "1",
				"paymentMethod": "creditCard",
				"touchpoint": "app",
				"remittanceInformation": "TARI 2021",
				"description": "TARI 2021",
				"metadata": [
					{
						"key": "1",
						"value": "22"
					}
				]
			},
			"transferList": [
				{
					"idTransfer": "1",
					"fiscalCodePA": "66666666666",
					"companyName": "PA paolo",
					"amount": "10.0",
					"transferCategory": "paGetPaymentTest",
					"remittanceInformation": "/RFB/00202200000217527/5.00/TXT/"
				}
			],
			"transactionDetails": {
				"user": {
					"fullName": "John Doe",
					"type": "F",
					"fiscalCode": FISCAL_CODE,
					"notificationEmail": "john.doe@mail.it",
					"userId": "1234",
					"userStatus": "11",
					"userStatusDescription": "REGISTERED_SPID"
				},
				"transaction": {
					"idTransaction": "123456",
					"transactionId": transactionId ? transactionId : "receipt-generator-int-test-transactionId",
					"grandTotal": 0,
					"amount": 0,
					"fee": 0
				}
			},
			"timestamp": 1679067463501,
			"properties": {
				"diagnostic-id": "00-f70ef3167cffad76c6657a67a33ee0d2-61d794a75df0b43b-01",
				"serviceIdentifier": "NDP002SIT"
			},
			"eventStatus": "DONE",
			"eventRetryEnrichmentCount": 0
		}
		arrayOfEvents.push(json_event);
	}

	return arrayOfEvents;
}

function createReceipt(id, status) {
	let receipt =
	{
		"eventId": id,
		"eventData": {
			"payerFiscalCode": TOKENIZED_FISCAL_CODE,
			"debtorFiscalCode": TOKENIZED_FISCAL_CODE,
			"amount": "200",
			"cart": [
				{
					"payeeName": "Comune di Milano",
					"subject": "ACI"
				},
				{
					"payeeName": "Comune di Milano",
					"subject": "ACI"
				},
				{
					"payeeName": "Comune di Milano",
					"subject": "ACI"
				},
			]
		},
		"status": status,
		"numRetry": 0,
		"id": id
	}
	return receipt
}

function createCart(id, status) {
	let cart =
	{
        "eventId": id,
        "id": "id" + id,
        "version": "1",
        "payload": {
            "payerFiscalCode": null,
            "transactionCreationDate": "2025-11-02T10:14:57.218496702Z",
            "totalNotice": "2",
            "totalAmount": "26,48",
            "mdAttachPayer": null,
            "idMessagePayer": null,
            "cart": [
                {
                    "bizEventId": "bz1"+id,
                    "subject": "oggetto 1",
                    "payeeName": "Ministero delle infrastrutture e dei trasporti",
                    "debtorFiscalCode": "a44d3ca2-f813-4188-bc24-da028accf981",
                    "amount": "16.0",
                    "mdAttach": null,
                    "idMessageDebtor": null,
                    "reasonErrDebtor": null
                },
                {
                    "bizEventId": "bz2"+id,
                    "subject": "oggetto 2",
                    "payeeName": "Ministero delle infrastrutture e dei trasporti",
                    "debtorFiscalCode": "a44d3ca2-f813-4188-bc24-da028accf981",
                    "amount": "10.2",
                    "mdAttach": null,
                    "idMessageDebtor": null,
                    "reasonErrDebtor": null
                }
            ],
            "reasonErrPayer": null
        },
        "status": status,
        "numRetry": 0,
        "notificationNumRetry": 0,
        "reasonErr": null,
        "inserted_at": 1762421981920,
        "generated_at": 0,
        "notified_at": 0
    }
	return cart
}

const getTokenizedBizEvent = (id, numberOfEvents) => {
	let arr = createEventsForQueue(id, numberOfEvents, null, "310391366991197059", "10391366991197059");
    return encryptText(JSON.stringify(arr));
}

const createErrorReceipt = (id, numberOfEvents) => {
	let payload = {
        "messagePayload": getTokenizedBizEvent(id, numberOfEvents),
        "bizEventId": id,
        "status": "REVIEWED",
        "id": id,
    };
	return payload;
}

module.exports = {
	sleep, createReceipt, createEventsForPoisonQueue, createEventsForQueue, createErrorReceipt
}