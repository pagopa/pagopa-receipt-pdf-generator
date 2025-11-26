const { BlobServiceClient } = require("@azure/storage-blob");

const blob_storage_conn_string = process.env.RECEIPTS_STORAGE_CONN_STRING || "";
const containerName = process.env.BLOB_STORAGE_CONTAINER_NAME;

const blobServiceClient = BlobServiceClient.fromConnectionString(blob_storage_conn_string);
const receiptContainerClient = blobServiceClient.getContainerClient(containerName);

async function receiptPDFExist(blobName) {
    const blobClient = receiptContainerClient.getBlobClient(blobName);
    return await blobClient.exists();
}

module.exports = {
    receiptPDFExist
}