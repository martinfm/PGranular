/*
 PGranular - a graphical front end to SuperCollider granular synthesis

 Copyright (C) 2015  Fiore Martin

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

(
var interpolation = 2;

s.waitForBoot( {

  [1,2].do { |numChannels| // build both mono and stereo version

    /* --- granular looper --- */
    SynthDef((numChannels == 2).if('granularLooper','granularLooperMono'),{ arg buffer,freq,dur,randPan, randStart,
      pos, pitch, posLFO, ampLFO, pitchLFO, maxPitchLFO, amp = 1, pan=0, out=0;

      var tick = Impulse.kr(freq);
      var player = TGrains.ar(
        numChannels:2,
        trigger:tick,
        bufnum:buffer,
        rate: pitch +  Select.kr(pitchLFO > 0, [0,Lag2.kr(LFSaw.kr(pitchLFO, mul:maxPitchLFO))]),
        centerPos:pos + (dur/2) + TRand.kr(0, randStart, tick) + Select.kr(posLFO > 0, [0,LFSaw.kr(posLFO).range(0,dur)]) ,
        dur:dur ,
        pan:pan+WhiteNoise.kr(randPan),
        amp: amp,
        interp:interpolation) *  Select.kr(ampLFO > 0, [1,Lag2.kr(SinOsc.kr(ampLFO, mul:0.5, add:1),0.1)]);
      /* sum if this is a mono synth */
      if(numChannels == 1) { player = player.sum };
      /* EnvGen smooths out */
      Out.ar(out,player*EnvGen.ar(Env.new([0,1],[0.1],'cubed')));
    }).add();

    /* --- granular player --- */
    SynthDef((numChannels == 2).if('granularPlayer','granularPlayerMono'),{ arg buffer,freq, dur,randPan,
      randStart, pos, pitch, posLFO, ampLFO, pitchLFO, maxPitchLFO, amp=1,
      pan=0, gate=1, att=0.01, dec=0.05, out=0;

      var tick = Impulse.kr(freq);
      var player = TGrains2.ar(
        numChannels:2,
        trigger:tick,
        bufnum:buffer,
        rate: pitch + Select.kr(pitchLFO > 0, [0,SinOsc.ar(pitchLFO).range(0,maxPitchLFO)]),
        centerPos:pos + (dur/2) + TRand.kr(0, randStart, tick) +
        Select.kr(posLFO > 0, [0,SinOsc.kr(posLFO).range(0,dur)]),
        dur:dur ,
        pan:pan+WhiteNoise.kr(randPan),
        amp: amp,
        att:att,
        dec:dec,
        interp:interpolation) * Select.kr(ampLFO > 0, [1,SinOsc.kr(ampLFO, mul:0.5, add:1)]);
      /* sum if this is a mono synth */
      if(numChannels == 1) { player = player.sum };

      Out.ar(out,
        player * EnvGen.ar(envelope:Env.adsr(attackTime: att, releaseTime: dec, sustainLevel:1),
          gate:gate,
          doneAction:2)
      );
    }).add();

    // /* --- granular beat --- */
    SynthDef((numChannels == 2).if('granularBeat','granularBeatMono'),{ arg buffer,freq,loopLen,
      dur,randPan, randStart, pos, pitch,ampLFO, pitchLFO, posLFO, maxPitchLFO,
      amp=1, pan=0, att=0.01, dec=1.0, out=0;

      var tick = Impulse.kr(freq);
      var player = TGrains2.ar(
        numChannels:2,
        trigger:tick,
        bufnum:buffer,
        rate: pitch + Select.kr(pitchLFO > 0, [0,SinOsc.ar(pitchLFO).range(0,maxPitchLFO)]),
        centerPos:pos + (loopLen/2) + Rand.new(0, randStart) +
          Select.kr(posLFO > 0, [0,SinOsc.kr(posLFO).range(0,dur)]),
        dur:loopLen,
        pan:pan+WhiteNoise.kr(randPan),
        amp: amp,
        att:0.01,
        dec:4,
        interp:interpolation) * Select.kr(ampLFO > 0, [1,SinOsc.kr(ampLFO, mul:0.5, add:1)]);
      /* sum if this is a mono synth */
      if(numChannels == 1) { player = player.sum };

      Out.ar(out,player*EnvGen.ar(Env.perc(attackTime: att, releaseTime:dec),doneAction:2));
    }).add();

  }; // [1,2].do


  SynthDef('micRecorder',{ arg buffer, in, run = 1;
    RecordBuf.ar(SoundIn.ar(in),buffer, run:run ,doneAction:2, loop:0);
  }).add();

  "Synth Defs created".postln;
}); // waitForBoot

)