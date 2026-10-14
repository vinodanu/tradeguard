try { rs.status(); } catch { rs.initiate({_id:'rs0',members:[{_id:0,host:'mongo:27017'}]}); }
let primary=false;
for(let i=0;i<60;i++){if(db.hello().isWritablePrimary){primary=true;break;}sleep(1000);}
if(!primary)throw new Error('Replica set did not elect a primary');
for(const [name,variable] of [['compliance','COMPLIANCE_DB_PASSWORD'],['liveview','LIVE_DB_PASSWORD']]){
 const database=db.getSiblingDB(name);
 if(!database.getUser(name))database.createUser({user:name,pwd:process.env[variable],roles:[{role:'readWrite',db:name}]});
}
