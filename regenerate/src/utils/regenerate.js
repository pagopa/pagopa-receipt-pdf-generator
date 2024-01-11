const fs = require('fs');

const rec_service_host = process.env.REC_SERVICE_HOST;

const { getReceiptsToProcess, post } = require("./utils");


function sleep(ms) {
	return new Promise(resolve => setTimeout(resolve, ms));
}

console.log("start re-generate...")

const start = async function (a, b) {

  const res = getReceiptsToProcess();

  let p = res.then(async function (result) {


    for (const e of result.resources) {
        const rsp = post(rec_service_host+"receipts/"+e.eventId+"/regenerate-receipt-pdf", {}, {})
        rsp.then( function(r) {
          console.log(`${e.eventId} : ${r.status}`);
        });
        await sleep(700);
    }

    // result.resources.forEach(async e => {
    //     // console.log(e);
    //     await sleep(5000);

    //     const rsp = post(rec_service_host+"receipts/"+e.eventId+"/regenerate-receipt-pdf", {}, {})
    //     rsp.then( function(r) {
    //       console.log(`${e.eventId} : ${r.status}`);
    //     });

    //     // return true;
    // })

  });

};

start();



