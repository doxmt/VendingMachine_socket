const { MongoClient } = require('mongodb');
const crypto = require('crypto');

const uri = process.env.MONGO_URI; // .env 파일에 넣어주세요
const dbName = 'vending_machine_server';
const AES_KEY = Buffer.from(process.env.AES_KEY.trim(), 'utf-8');

// AES 암호화 함수 (ECB 모드)
function encryptAES(text) {
  const cipher = crypto.createCipheriv('aes-128-ecb', AES_KEY, null);
  cipher.setAutoPadding(true);
  let encrypted = cipher.update(text, 'utf8', 'base64');
  encrypted += cipher.final('base64');
  return encrypted;
}

// AES 복호화 함수 (ECB 모드)
function decryptAES(text) {
  const decipher = crypto.createDecipheriv('aes-128-ecb', AES_KEY, null);
  decipher.setAutoPadding(true);
  let decrypted = decipher.update(text, 'base64', 'utf8');
  decrypted += decipher.final('utf8');
  return decrypted;
}

class ServerMongoDBManager {
  static instance;

  constructor() {
    this.client = new MongoClient(uri);
    this.db = null;
  }

  async connect() {
    if (!this.db) {
      await this.client.connect();
      this.db = this.client.db(dbName);
    }
  }

  static getInstance() {
    if (!ServerMongoDBManager.instance) {
      ServerMongoDBManager.instance = new ServerMongoDBManager();
    }
    return ServerMongoDBManager.instance;
  }

  async updateDrinkNameEverywhere(vmNumber, oldName, newName) {
    try {
      await this.connect();

      const encVm = encryptAES(vmNumber.trim().toUpperCase());
      const encOldName = encryptAES(oldName);
      const encNewName = encryptAES(newName);

      const salesFilter = { vmNumber: encVm, drinkName: encOldName };
      const salesUpdate = { $set: { drinkName: encNewName } };
      await this.db.collection('sales').updateMany(salesFilter, salesUpdate);

      const inventoryFilter = { vmNumber: encVm, name: encOldName };
      const inventoryUpdate = { $set: { name: encNewName } };
      await this.db.collection('inventory').updateMany(inventoryFilter, inventoryUpdate);

      const drinksFilter = { vmNumber: encVm, name: encOldName };
      const drinksUpdate = { $set: { name: encNewName } };
      await this.db.collection('drinks').updateMany(drinksFilter, drinksUpdate);

      console.log(`[서버] 음료 이름 변경 완료: ${oldName} → ${newName}`);
    } catch (error) {
      console.error('[서버] 음료 이름 변경 중 오류:', error);
      throw error;
    }
  }
}

module.exports = ServerMongoDBManager;
