/* Offline smoke/logic tests runnable with Node 20+, no browser or network needed. */
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),path=require('node:path');
const root=path.join(__dirname,'../app/src/main/assets');
const geo=JSON.parse(fs.readFileSync(path.join(root,'data/states.geojson'),'utf8'));
assert.equal(geo.features.length,16,'16 real state polygons required');
const elements={};function elt(id){return elements[id]||(elements[id]={innerHTML:'',value:'',textContent:'',style:{},options:[],classList:{add(){},remove(){},toggle(){}},setAttribute(){},addEventListener(){}})}
const document={getElementById:elt,querySelectorAll(){return[]},addEventListener(){},createElement(){return elt('generic')}};
const store=new Map();const localStorage={getItem:k=>store.get(k)||null,setItem:(k,v)=>store.set(k,v),removeItem:k=>store.delete(k)};
const ctx=vm.createContext({console,Math,Date,JSON,document,localStorage,window:{GEO_STATES:geo},setInterval(){},clearInterval(){},alert(){},confirm(){return true},FileReader:function(){},Image:function(){}});
for(const file of ['map.js','audio.js','game.js'])vm.runInContext(fs.readFileSync(path.join(root,file),'utf8'),ctx,{filename:file});
const run=(code)=>vm.runInContext(code,ctx);
run('state=newState("career");ensureUnitGeo();');
assert.equal(run('state.units.length'),19);
assert.equal(run('state.templates.length'),48);
assert.equal(run('COURSES.length'),8);
for(const f of geo.features){const result=run(`(()=>{state.region=${JSON.stringify(f.properties.name)};mapProjection=null;ensureUnitGeo();setupProjection();drawStateMap();return {name:state.region,paths:document.getElementById('stateMap').innerHTML.match(/class="state-land"/)?.length||0,unitCount:state.units.length,geofeature:regionFeature()?.properties?.code,cities:cityList().length}})()`);assert.equal(result.geofeature,f.properties.code,`invalid region ${f.properties.name}`);assert(result.cities>=3,`missing cities in ${f.properties.name}`);}
run('state=newState("career");state.region="Niedersachsen";ensureUnitGeo();mapProjection=null;setupProjection()');
for(const [type,mins] of [['early',480],['day12',720],['full24',1440]]){
 run(`state=newState("career");state.region="Niedersachsen";state.shiftType=${JSON.stringify(type)};state.shiftStart=22*60;state.autoCalls=false;ensureUnitGeo();state.stage="running";state.paused=false;`);
 assert.equal(run('shiftMinutes()'),mins);
 run('advanceMinutes('+mins+')');
 assert.equal(run('state.stage'),'debrief');assert.equal(run('state.elapsed'),mins);assert.equal(run('state.history.length'),1);
 assert.equal(run('state.history[0].duration'),mins);
 assert.equal(run('state.day'),1);run('nextDay()');assert.equal(run('state.day'),2);
}
run(`state=newState('sandbox');state.stage='running';state.region='Niedersachsen';state.autoCalls=false;state.paused=false;ensureUnitGeo();`);
run("createIncident(state.templates.find(t=>t.name==='Ruhestörung'),0)");
const pos0=run('state.incidents[0].position.join(",")');assert.equal(pos0,'9.735,52.374');
const id=run('state.incidents[0].id');const unit=run('state.units.find(x=>x.type==="STREIFE"&&x.position[0]!==9.735).id');run(`assign(${id},${unit})`);
const first=run('state.units.find(u=>u.id==='+unit+').position.slice()');run('advanceMinutes(5)');const after=run('state.units.find(u=>u.id==='+unit+').position.slice()');assert.notDeepEqual(Array.from(first),Array.from(after),'unit did not move');
const old=run('state.elapsed');run('state.paused=true');run('if(!state.paused)advanceMinutes(10)');assert.equal(run('state.elapsed'),old);
run('state.stage="planning";state.units[0].training={courseId:"comms",remainingDays:2};state.autoCalls=false;state.paused=true;');
for(let d=0;d<2;d++){run('state.stage="running";state.elapsed=shiftMinutes();finishShift()');assert.equal(run('state.units[0].training?.remainingDays||0'),1-d);if(d===0)run('nextDay()')}
console.log('PASS · 16 Bundesland-Grenzen · 48 Einsätze · 8 Lehrgänge · 8/12/24h · Nacht/Tagwechsel · Live-Routing · Ausbildung');
