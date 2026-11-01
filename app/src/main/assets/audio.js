/* Original per WebAudio synthetisierte SFX; keine Tonaufnahmen, kein Netzwerk, keine externen Assets. */
'use strict';
const audioSettings={enabled:false,volume:.32};let audioContext=null;let audioLast={};
try{const s=JSON.parse(localStorage.getItem('el110-audio-settings')||'null');if(s){audioSettings.enabled=!!s.enabled;audioSettings.volume=Math.max(0,Math.min(1,Number(s.volume)||0));}}catch(e){}
function audioStart(){if(!audioSettings.enabled)return;try{const A=window.AudioContext||window.webkitAudioContext;if(!A)return;if(!audioContext)audioContext=new A();if(audioContext.state==='suspended')audioContext.resume()}catch(e){console.warn('Audio deaktiviert',e)}}
function soundTone(freq,start,duration,kind='sine',gain=.1){if(!audioContext)return;const osc=audioContext.createOscillator(),amp=audioContext.createGain(),now=audioContext.currentTime+start;osc.type=kind;osc.frequency.setValueAtTime(freq,now);osc.frequency.exponentialRampToValueAtTime(Math.max(35,freq*.83),now+duration);amp.gain.setValueAtTime(.0001,now);amp.gain.exponentialRampToValueAtTime(Math.max(.0002,gain*audioSettings.volume),now+.014);amp.gain.exponentialRampToValueAtTime(.0001,now+duration);osc.connect(amp);amp.connect(audioContext.destination);osc.start(now);osc.stop(now+duration+.01)}
function soundNoise(start=.0,dur=.16,volume=.05){if(!audioContext)return;const rate=audioContext.sampleRate,buffer=audioContext.createBuffer(1,Math.ceil(rate*dur),rate),samples=buffer.getChannelData(0);for(let i=0;i<samples.length;i++)samples[i]=(Math.random()*2-1)*(1-i/samples.length);const src=audioContext.createBufferSource(),vol=audioContext.createGain();src.buffer=buffer;vol.gain.value=audioSettings.volume*volume;src.connect(vol);vol.connect(audioContext.destination);src.start(audioContext.currentTime+start)}
function playCue(kind){if(!audioSettings.enabled)return;const now=Date.now();if(now-(audioLast[kind]||0)<600)return;audioLast[kind]=now;audioStart();if(!audioContext)return;
 switch(kind){
 case 'call':soundTone(700,0,.12,'sine',.11);soundTone(900,.15,.13,'sine',.1);break;
 case 'p1':soundTone(480,0,.16,'sawtooth',.09);soundTone(735,.18,.17,'sawtooth',.09);soundTone(560,.37,.16,'sawtooth',.08);break;
 case 'p2':soundTone(610,0,.14,'triangle',.09);soundTone(780,.2,.12,'triangle',.09);break;
 case 'radio':soundNoise(0,.13,.12);break;
 case 'dispatch':soundNoise(0,.08,.06);soundTone(950,.10,.12,'square',.06);break;
 case 'departure':soundTone(145,0,.15,'sawtooth',.025);soundTone(185,.1,.18,'triangle',.02);break;
 case 'success':soundTone(587,0,.12,'triangle',.09);soundTone(784,.14,.16,'triangle',.09);soundTone(988,.32,.22,'triangle',.07);break;
 case 'failure':soundTone(392,0,.18,'sawtooth',.075);soundTone(293,.22,.22,'triangle',.07);break;
 case 'shift':soundTone(392,0,.2,'sine',.065);soundTone(523,.18,.2,'sine',.065);soundTone(659,.4,.26,'sine',.08);break;
 case 'click':soundTone(1100,0,.05,'sine',.025);break;
 default:soundTone(550,0,.12,'sine',.05);
 }
}
function initAudioUI(){const c=document.getElementById('audioEnabled'),v=document.getElementById('audioVolume');if(c)c.checked=audioSettings.enabled;if(v)v.value=Math.round(audioSettings.volume*100)}
function setAudioEnabled(v){audioSettings.enabled=!!v;localStorage.setItem('el110-audio-settings',JSON.stringify(audioSettings));audioStart();if(v)playCue('success')}
function setAudioVolume(value){audioSettings.volume=Math.max(0,Math.min(1,Number(value)/100));localStorage.setItem('el110-audio-settings',JSON.stringify(audioSettings));audioStart()}
function previewAudio(){if(!audioSettings.enabled)setAudioEnabled(true);else playCue('p2')}
document.addEventListener('visibilitychange',()=>{if(audioContext&&document.hidden)audioContext.suspend();else if(audioContext&&!document.hidden&&audioSettings.enabled)audioContext.resume()});
