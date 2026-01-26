const axios = require("axios");

const helpdesk_url = process.env.HELPDESK_URL;

axios.defaults.headers.common['Ocp-Apim-Subscription-Key'] = process.env.HELPDESK_SUBKEY || ""; // for all requests

async function postRegenerateReceiptPdf(eventId) {
	let endpoint = process.env.REGENERATE_RECEIPT_PDF_ENDPOINT || "receipts/{bizevent-id}/regenerate-receipt-pdf";
	endpoint = endpoint.replace("{bizevent-id}", eventId);

	return await axios.post(helpdesk_url + endpoint, {})
		.then(res => {
			return res;
		})
		.catch(error => {
			return error.response;
		});
}

async function postRegenerateCartReceiptPdf(cartId) {
	let endpoint = process.env.REGENERATE_RECEIPT_PDF_ENDPOINT || "cart-receipts/{cart-id}/regenerate-receipt-pdf";
	endpoint = endpoint.replace("{cart-id}", cartId);

	return await axios.post(helpdesk_url + endpoint, {})
		.then(res => {
			return res;
		})
		.catch(error => {
			return error.response;
		});
}

module.exports = {
	postRegenerateReceiptPdf, postRegenerateCartReceiptPdf
}