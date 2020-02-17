import initSqlJs from 'sql.js';

// based on https://github.com/kripken/sql.js/blob/master/src/worker.coffee
const sqlReady = initSqlJs();
let db = null;

onmessage = e => {
    sqlReady.then(() => {
        const data = e.data;
        let res = null;
        switch (data.action) {
        case "open":
            if (db) throw new Error("db already exists");
            db = new SQL.Database(data.buffer ? new Uint8Array(data.buffer) : undefined);
            break;
        case "exec":
            res = db.exec(data.sql);
            break;
        case "transaction":
            db.run("BEGIN TRANSACTION");
            try {
                for (const s of data.statements) {
                    db.run(s[0], s[1]);
                }
            } catch(e) {
                db.run("ROLLBACK TRANSACTION");
                throw e;
            }
            db.run("COMMIT TRANSACTION");
            break;
        case "export":
            res = db.export();
            break;
        }
        postMessage({"result": res});
    }).catch(e => {
        console.log("worker error!", e);
        postMessage({"error": e.message});
    });
};
