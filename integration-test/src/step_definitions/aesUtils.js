const crypto = require('crypto');

const AES_SECRET_KEY = process.env.AES_SECRET_KEY;  // Sostituisci con la tua chiave segreta
const AES_SALT = process.env.AES_SALT;  // Sostituisci con il tuo salt
const ITERATION_COUNT = 65536;
const KEY_LENGTH = 256;
const ALGORITHM = 'aes-256-cbc';

function encryptText(strToEncrypt) {
    try {
        const iv = crypto.randomBytes(16);
        const key = crypto.pbkdf2Sync(AES_SECRET_KEY, AES_SALT, ITERATION_COUNT, KEY_LENGTH / 8, 'sha256');
        const cipher = crypto.createCipheriv(ALGORITHM, key, iv);

        let encryptedData = cipher.update(strToEncrypt, 'utf-8', 'base64');
        encryptedData += cipher.final('base64');

        const encryptedDataWithIv = Buffer.concat([iv, Buffer.from(encryptedData, 'base64')]);

        return encryptedDataWithIv.toString('base64');
    } catch (error) {
        console.error('Error during encryption:', error.message);
    }
}

module.exports = { encryptText };
