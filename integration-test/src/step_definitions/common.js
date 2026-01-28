const FISCAL_CODE = "AAAAAA00A00A000A";
const PAYER_FISCAL_CODE = "BBAAAA00A00A000A";
const TOKENIZED_FISCAL_CODE = "cd07268c-73e8-4df4-8305-a35085e32eff";
const { encryptText } = require("./aesUtils");
const STANDARD_NOTICE_NUMBER = "310391366991197059"
const IUV = "10391366991197059"

function sleep(ms) {
	return new Promise(resolve => setTimeout(resolve, ms));
}

function createEventsForQueue(id, numberOfEvents, transactionId, noticeNumber, iuv) {
	return createEventsForPoisonQueue(id, false, numberOfEvents, transactionId, noticeNumber, iuv);
}

function createEventsForPoisonQueue(id, attemptedPoisonRetry, numberOfEvents, transactionId, noticeNumber, iuv) {
	let arrayOfEvents = [];
	for (let i = 0; i < (numberOfEvents ?? 1); i++) {
		let finalId = id + (i != 0 ? i : "");
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
				}
			]
		},
		"status": status,
		"numRetry": 0,
		"id": id
	}
	return receipt
}

function createCart(id, cartId, status) {
	let cart =
	{
		"cartId": cartId,
		"id": id,
		"version": "1",
		"payload": {
			"payerFiscalCode": PAYER_FISCAL_CODE,
			"transactionCreationDate": "2025-11-02T10:14:57.218496702Z",
			"totalNotice": "2",
			"totalAmount": "26,48",
			"mdAttachPayer": null,
			"messagePayer": null,
			"cart": [
				{
					"bizEventId": "bz1" + id,
					"subject": "oggetto 1",
					"payeeName": "Ministero delle infrastrutture e dei trasporti",
					"debtorFiscalCode": FISCAL_CODE,
					"amount": "16.0",
					"mdAttach": null,
					"messageDebtor": null,
					"reasonErrDebtor": null
				},
				{
					"bizEventId": "bz2" + id,
					"subject": "oggetto 2",
					"payeeName": "Ministero delle infrastrutture e dei trasporti",
					"debtorFiscalCode": FISCAL_CODE,
					"amount": "10.2",
					"mdAttach": null,
					"messageDebtor": null,
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

function createCartForRegenerate(id, cartId, status) {
	let cart =
	{
		"cartId": cartId,
		"id": id,
		"version": "1",
		"payload": {
			"payerFiscalCode": PAYER_FISCAL_CODE,
			"transactionCreationDate": "2025-11-02T10:14:57.218496702Z",
			"totalNotice": "2",
			"totalAmount": "26,48",
			"mdAttachPayer": status === "IO_NOTIFIED" ? {
				"name": "pagopa-ricevuta-251204-test-ricevute-carrello-363eb6c9-781a-4b62-87d7-b5365d2e9b55-0-p-c.pdf",
				"url": "https://pagopauweureceiptsfnsa.blob.core.windows.net/pagopa-u-weu-receipts-azure-blob-receipt-st-attach/pagopa-ricevuta-251204-test-ricevute-carrello-363eb6c9-781a-4b62-87d7-b5365d2e9b55-0-p-c.pdf"
			} : null,
			"messagePayer": status === "IO_NOTIFIED" ? {
				"id": "01KBMCY97TG0TQJ70RQBE61GFK",
				"subject": "Ricevuta di pagamento",
				"markdown": "Hai effettuato il pagamento di 5 avvisi:\n\n# Avviso 1\n\n**Importo:** 16,00 €\n**Oggetto:** Pagamento multa 1\n**Ente creditore:** Ministero delle infrastrutture e dei trasporti\n\n# Avviso 2\n\n**Importo:** 10,20 €\n**Oggetto:** Pagamento multa 2\n**Ente creditore:** Ministero delle infrastrutture e dei trasporti\n\n# Avviso 3\n\n**Importo:** 25,00 €\n**Oggetto:** Pagamento multa 3\n**Ente creditore:** Ministero delle infrastrutture e dei trasporti\n\n# Avviso 4\n\n**Importo:** 34,50 €\n**Oggetto:** Pagamento multa 4\n**Ente creditore:** Ministero delle infrastrutture e dei trasporti\n\n# Avviso 5\n\n**Importo:** 91,00 €\n**Oggetto:** Pagamento multa 5\n**Ente creditore:** Ministero delle infrastrutture e dei trasporti\n\n\nEcco la ricevuta con i dettagli."
			} : null,
			"cart": [
				{
					"bizEventId": `${id}-1`,
					"subject": "oggetto 1",
					"payeeName": "Ministero delle infrastrutture e dei trasporti",
					"debtorFiscalCode": FISCAL_CODE,
					"amount": "16.0",
					"mdAttach": status === "IO_NOTIFIED" ? {
						"name": "pagopa-ricevuta-251204-doc-test-ricevute-31b43ccf-9cbb-4637-9027-415303e7c1d1-0-1-d-c.pdf",
						"url": "https://pagopauweureceiptsfnsa.blob.core.windows.net/pagopa-u-weu-receipts-azure-blob-receipt-st-attach/pagopa-ricevuta-251204-doc-test-ricevute-31b43ccf-9cbb-4637-9027-415303e7c1d1-0-1-d-c.pdf"
					} : null,
					"messageDebtor": status === "IO_NOTIFIED" ? {
						"id": "01KBMCY9HPXHQQHTDPBC1KF0AJ",
						"subject": "Ricevuta del pagamento a Ministero delle infrastrutture e dei trasporti",
						"markdown": "È stato effettuato il pagamento di un avviso intestato a te:\n\n**Importo**: 10,20 €\n\n**Oggetto:** Pagamento multa 2\n\n**Ente creditore**: Ministero delle infrastrutture e dei trasporti\n\nEcco la ricevuta con i dettagli."
					} : null,
					"reasonErrDebtor": null
				},
				{
					"bizEventId": `${id}-2`,
					"subject": "oggetto 2",
					"payeeName": "Ministero delle infrastrutture e dei trasporti",
					"debtorFiscalCode": FISCAL_CODE,
					"amount": "10.2",
					"mdAttach": status === "IO_NOTIFIED" ? {
						"name": "pagopa-ricevuta-251204-doc-test-ricevute-31b43ccf-9cbb-4637-9027-415303e7c1d1-0-4-d-c.pdf",
						"url": "https://pagopauweureceiptsfnsa.blob.core.windows.net/pagopa-u-weu-receipts-azure-blob-receipt-st-attach/pagopa-ricevuta-251204-doc-test-ricevute-31b43ccf-9cbb-4637-9027-415303e7c1d1-0-4-d-c.pdf"
					} : null,
					"messageDebtor": status === "IO_NOTIFIED" ? {
						"id": "01KBMCY9RXWFMGPPCDYKRD1GG8",
						"subject": "Ricevuta del pagamento a Ministero delle infrastrutture e dei trasporti",
						"markdown": "È stato effettuato il pagamento di un avviso intestato a te:\n\n**Importo**: 34,50 €\n\n**Oggetto:** Pagamento multa 4\n\n**Ente creditore**: Ministero delle infrastrutture e dei trasporti\n\nEcco la ricevuta con i dettagli."
					} : null,
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

const createErrorCart = (id, eventId) => {
	let payload = {
		"id": id,
		"bizEventId": eventId,
		"messagePayload": encryptText(JSON.stringify(createEventsForCartQueue(id, 2, eventId, STANDARD_NOTICE_NUMBER, IUV))),
		"messageError": encryptText("error message"),
		"status": "REVIEWED"
	};
	return payload;
}


function createEventsForCartQueue(id, numberOfEvents, transactionId, noticeNumber, iuv) {
	return createBizEvents(id, false, numberOfEvents, transactionId, noticeNumber, iuv);
}

function createBizEvents(id, attemptedPoisonRetry, numberOfEvents, transactionId, noticeNumber, iuv) {
	let arrayOfEvents = [];
	for (let i = 0; i < (numberOfEvents ?? 1); i++) {
		let finalId = "bz" + (i + 1) + id;
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
				"entityUniqueIdentifierValue": PAYER_FISCAL_CODE,
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
					"fiscalCode": PAYER_FISCAL_CODE,
					"notificationEmail": "john.doe@mail.it",
					"userId": "1234",
					"userStatus": "11",
					"userStatusDescription": "REGISTERED_SPID"
				},
				"transaction": {
					"idTransaction": "123456",
					"transactionId": transactionId,
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

function createEvent(id, status, transactionId, totalNotice, orgCode, iuv) {
	let json_event = {
		"id": id,
		"version": "2",
		"idPaymentManager": "54927408",
		"complete": "false",
		"receiptId": "9851395f09544a04b288202299193ca6",
		"missingInfo": [
			"psp.pspPartitaIVA",
			"paymentInfo.primaryCiIncurredFee",
			"paymentInfo.idBundle",
			"paymentInfo.idCiBundle"
		],
		"debtorPosition": {
			"modelType": "2",
			"noticeNumber": "310391366991197059",
			"iuv": iuv || "iuv"
		},
		"creditor": {
			"idPA": orgCode || "orgCode",
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
			"totalNotice": totalNotice || "1",
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
				"fiscalCode": PAYER_FISCAL_CODE,
				"notificationEmail": "john.doe@mail.it",
				"userId": "1234",
				"userStatus": "11",
				"userStatusDescription": "REGISTERED_SPID"
			},
			"transaction": {
				"idTransaction": "123456",
				"transactionId": transactionId || "123456",
				"grandTotal": 0,
				"amount": 1000,
				"fee": 0,
				"origin": "IO"
			}
		},
		"timestamp": 1679067463501,
		"properties": {
			"diagnostic-id": "00-f70ef3167cffad76c6657a67a33ee0d2-61d794a75df0b43b-01",
			"serviceIdentifier": "NDP002SIT"
		},
		"eventStatus": status || "DONE",
		"eventRetryEnrichmentCount": 0
	}
	return json_event
}


module.exports = {
	sleep, createReceipt, createEventsForPoisonQueue, createEventsForQueue, createErrorReceipt, createCart, createErrorCart, createEventsForCartQueue, createEvent, createCartForRegenerate
}