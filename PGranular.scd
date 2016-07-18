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
var window, openBtn, recordBtn, midiBtn, midiPanel, wave, buffer = nil, durSlider, playBtn, saveConfigBtn,
loadConfigBtn, saveRecordBtn, currentSelectionBox, volumeFaders, panKnobs, zoomFactor = 1.2, ampLFO,
charCodes, synths = nil, randomStartNumBox, randomPanNumBox, c_BUTTON_SIZE = Rect(90, 20, 100, 30),
c_NUM_SELECTIONS = 10, makeResetActionFunc, recorder, pitchSlider, reverseCheck, dedicatedOutNumBoxes,
midiEnableBtns, scopeBtn, lfoView, posLFO, pitchLFO, pitchLFOSlider, lfoLabel, ctrlsView, dedicatedOutEnableBtns,
playAction, recordAction, additionalActions, attackNumBox, decayNumBox, selectionStepNumBox, filters,
c_DISABLED_OUT = -1, bufferLenNumBox, inNumBox, outNumBox, additionalControls,
c_SUBVIEW_COLOR = Color.new255(204,204,204);

/* load the synths that will be played by the PGranular */
"./PGranularSynths.scd".loadRelative;


charCodes = IdentityDictionary[
  $o -> 79,
  $r -> 82,
  'Esc' -> 27,
  'SpaceBar' -> 32,
  'Up' -> 38,
  'Down' -> 40,
  'Left' -> 37,
  'Right' -> 39,
  'Tab' -> 9,
  'PageUp' -> 33,
  'PageDown' -> 34,
  'Equal'-> 187,
  'Minus' -> 189,
  'Ins' -> 45,
  'F1' -> 112,
  'F2' -> 113,
  'GraveAccent' -> 223
];

/* -------------------------INIT ADDITIONAL CONTROLS -----------------------------------*/
additionalControls = (); /* has attack, decay, bufferLen, selectionStep, in,
                           out, dedicatedOuts, midiEnabledSelections */
additionalControls.attack = 0.01;
additionalControls.decay = 1.0;
additionalControls.bufferLen = 4;
additionalControls.selectionStep = 10;
additionalControls.in = 0;
additionalControls.out = 0;


/* dedicated outs is an array of integers, each holding the out of each selection or c_DISABLED_OUT if disabled */
additionalControls.dedicatedOuts = c_DISABLED_OUT ! c_NUM_SELECTIONS;

/* midiEnabledSelections is an array of boolean indicating whether a selection should be played in the looper or in player */
additionalControls.midiEnabledSelections =  false ! c_NUM_SELECTIONS;

/*---------------------------------BUILD GUI-------------------------------------------*/

window = Window.new(" __ P-Granular __", Rect(20,50, 1020, 640))/*.alwaysOnTop_(true)*/;
window.view.decorator = FlowLayout(window.view.bounds).gap_(5 @ 5).margin_(10 @ 10);

wave = PGranularWave.new(window, Rect(20,20, 1000, 260),c_NUM_SELECTIONS);

/* add this wave to the global list of waves to keep track
* of the waves if multiple instances  of grain window are open */
if(~pgranularwaves.isNil,{
  ~pgranularwaves = List.new(5);
});
~pgranularwaves.add(wave);

currentSelectionBox = EZNumber.new(
  parent:window,
  bounds:75@20,
  controlSpec:wave.controlSpecOf('currentSelection'),
  action:{arg ezNum;  wave.currentSelection_(ezNum.value.asInteger);},
  label:"selection:",
  labelWidth:50);

durSlider = EZSlider.new(
  parent: window,
  bounds: 275@20,
  label: "duration:",
  controlSpec: wave.controlSpecOf('durFactor'),
  action: {arg ezSlider;  wave.durFactor_(wave.currentSelection, ezSlider.value);},
  initVal: 1,
  numberWidth:60,
  layout:\horz
);

pitchSlider = EZSlider.new(
  parent: window,
  bounds: 350@20,
  label: "pitch:",
  controlSpec: wave.controlSpecOf('pitchFactor'),
  action: {arg ezSlider;  wave.pitchFactor_(wave.currentSelection, ezSlider.value);},
  initVal: 1,
  numberWidth:60,
  layout:\horz
);

reverseCheck = CheckBox.new(window,20@20).action_({arg r;
  var currentSelectionIndex = wave.currentSelection;
  wave.reversed_(currentSelectionIndex, r.value);
});

randomStartNumBox = EZNumber.new(
  parent:window,
  bounds: 120@20,
  label: "random start:",
  controlSpec:  wave.controlSpecOf('randomStart'),
  action: {arg box; wave.randomStart_(wave.currentSelection, box.value)},
  labelWidth: 70,
  numberWidth: 50,
  initVal:0);

randomPanNumBox = EZNumber.new(
  parent:window,
  bounds: 120@20,
  label: "random pan:",
  controlSpec: wave.controlSpecOf('randomPan'),
  action: {arg box; wave.randomPan_(wave.currentSelection, box.value)},
  numberWidth: 50,
  initVal:0);

/* leave a bit of space */
5.do({window.view.decorator.nextLine;});

panKnobs = Array.fill(c_NUM_SELECTIONS,{arg i;
  var knob = Knob.new(
    parent:window,
    bounds:Rect(0, 0, 50, 50)
  );
  knob.centered_(false)
  .value_(wave.controlSpecOf('pan').unmap(0))
  .action_({arg k;
    wave.pan_(i, wave.controlSpecOf('pan').map(k.value) );
  });
});
window.view.decorator.nextLine;
volumeFaders = Array.fill(c_NUM_SELECTIONS,{arg i;
  EZSlider.new(
    parent:window,
    bounds:50@150,
    label:nil,
    controlSpec:wave.controlSpecOf('amplitude'),
    action:{arg fader;
      wave.amplitude_(i,fader.value.dbamp);
    },
    layout:'vert',
    initVal:1.ampdb
  );
});

/* leave a bit of space*/
View.new(window,15@30);


lfoView = View.new(window, 220@150).background_(c_SUBVIEW_COLOR);

lfoLabel = StaticText.new(lfoView,Rect(95,10,25,10)).string_("LFO").align_(\center)
.font_(Font.new(Font.default.name,12,true));

posLFO = EZKnob(lfoView,
  Rect(10,30,50,95),
  "position",
  wave.controlSpecOf('posLFO'),
  { arg lfo; wave.posLFO_(wave.currentSelection,lfo.value)}
);
posLFO.labelView.align_(\center);

ampLFO = EZKnob(lfoView,
  Rect(73,30,50,95),
  "amplitude",
  wave.controlSpecOf('ampLFO'),
  { arg lfo; wave.ampLFO_(wave.currentSelection,lfo.value)}
);
ampLFO.labelView.align_(\center);

pitchLFO = EZKnob(lfoView,
  Rect(133,30,50,95),
  "pitch",
  wave.controlSpecOf('pitchLFO'),
  {arg lfo; wave.pitchLFO_(wave.currentSelection, lfo.value )}
);
pitchLFO.labelView.align_(\center);

pitchLFOSlider = EZSlider.new(
  parent: lfoView,
  bounds: Rect(185,30,25,95),
  label: "max",
  controlSpec: wave.controlSpecOf('maxPitchLFO'),
  action: {arg ezSlider; wave.maxPitchLFO_(wave.currentSelection, ezSlider.value)},
  initVal: 1,
  numberWidth:80,
  layout:\vert
);

/* leave a bit of space*/
View.new(window,15@30);

ctrlsView = View.new(window, 180@150).background_(c_SUBVIEW_COLOR);


attackNumBox = EZNumber.new(
  parent:ctrlsView,
  bounds: Rect(5,10,80,20),
  label: "attack:",
  controlSpec: ControlSpec.new(0.01,4,step:0.01),
  action: {arg box; additionalControls.attack = box.value},
  initAction: true,
  labelWidth: 70,
  numberWidth: 37,
  initVal:additionalControls.attack);

decayNumBox = EZNumber.new(
  parent:ctrlsView,
  bounds: Rect(90,10,75,20),
  label: "decay:",
  controlSpec: ControlSpec.new(0.01,4,step:0.01),
  action: {arg box; additionalControls.decay = box.value},
  initAction: true,
  labelWidth: 80,
  numberWidth: 37,
  initVal:additionalControls.decay);

selectionStepNumBox = EZNumber.new(
  parent:ctrlsView,
  bounds: Rect(50,43,115,20),
  label: "selection step:",
  controlSpec: ControlSpec.new(1,inf,step:1),
  action: {arg box; additionalControls.selectionStep = box.value},
  initAction: true,
  labelWidth: 70,
  numberWidth: 37,
  initVal:additionalControls.selectionStep);


bufferLenNumBox = EZNumber.new(
  parent:ctrlsView,
  bounds: Rect(67,77,97,20),
  label: "buffer len:",
  controlSpec: ControlSpec.new(1,inf,step:1, units:"ms"),
  action: {arg box; additionalControls.bufferLen = box.value},
  labelWidth: 90,
  initAction: true,
  numberWidth: 37,
  initVal:additionalControls.bufferLen);


inNumBox = EZNumber.new(
  parent:ctrlsView,
  bounds: Rect(25,110,60,20),
  label: "in:",
  controlSpec: ControlSpec.new(0,inf,step:1),
  action: {arg box; additionalControls.in = box.value},
  initAction: true,
  labelWidth: 30,
  numberWidth: 37,
  initVal:additionalControls.in);

outNumBox = EZNumber.new(
  parent:ctrlsView,
  bounds: Rect(105,110,60,20),
  label: "out:",
  controlSpec: ControlSpec.new(0,inf,step:1),
  action: {arg box; additionalControls.out = box.value},
  initAction: true,
  labelWidth: 40,
  numberWidth: 37,
  initVal:additionalControls.out);

window.view.decorator.nextLine;

dedicatedOutEnableBtns = Array.newClear(c_NUM_SELECTIONS);
midiEnableBtns = Array.newClear(c_NUM_SELECTIONS);

(0..c_NUM_SELECTIONS-1).do { |i|
  var font = Font.default;
  font.size = 6.5;

  midiEnableBtns[i] = Button(window, 23@20)
  .states_([["MIDI"],["MIDI",Color.black,Color.new255(170,170,170)]]).font_(font)
  .action_({|thisBtn|
    /* enable this selection for the midi player */
    if(midiPanel.notNil){ additionalControls.midiEnabledSelections[i] = (thisBtn.value == 1) }
  });

  dedicatedOutEnableBtns[i] = Button(window, 22@20)
  .states_([["OUT"],["OUT",Color.black,Color.new255(170,170,170)]]).font_(font)
  .action_({|thisBtn|
    var bgcolor = (thisBtn.value == 0).if(c_SUBVIEW_COLOR, Color.white);
    dedicatedOutNumBoxes[i].setColors(numBackground:bgcolor).enabled_(thisBtn.value == 1);
    /* this is for the midi panel. it sets the dedicated out to out box value or disabled */
    additionalControls.dedicatedOuts[i] = (thisBtn.value == 0).if( c_DISABLED_OUT, {dedicatedOutNumBoxes[i].value});
  });

};


window.view.decorator.nextLine;

dedicatedOutNumBoxes = Array.fill(c_NUM_SELECTIONS, {|i|
  EZNumber.new(
    parent:window,
    bounds: 50@20,
    controlSpec: ControlSpec.new(0,inf,step:1),
    action: {arg box; additionalControls.dedicatedOuts[i] = box.value },
    initAction: false,
    initVal:0
  ).enabled_(false).setColors(numBackground:c_SUBVIEW_COLOR);
});


/* leave a bit of space */
4.do({window.view.decorator.nextLine;});
playBtn = Button.new(window,c_BUTTON_SIZE).states_([["Play",nil,nil],["Stop",nil,nil]]);
recordBtn = Button.new(window,c_BUTTON_SIZE).states_([["Record",nil,nil],["Stop",nil,nil]]);
View(window,55@20);
openBtn = Button.new(window,c_BUTTON_SIZE).states_([["Open",nil,nil]]);
saveRecordBtn = Button.new(window,c_BUTTON_SIZE).states_([["Save Record",nil,nil]]);
loadConfigBtn = Button.new(window,c_BUTTON_SIZE).states_([["Load Config",nil,nil]]);
saveConfigBtn = Button.new(window,c_BUTTON_SIZE).states_([["Save Config",nil,nil]]);
View(window,90@20);
midiBtn = Button.new(window,c_BUTTON_SIZE).states_([["Midi",nil,nil]]);
scopeBtn = Button.new(window,c_BUTTON_SIZE).states_([["Scope",nil,nil]]);

/*--------------------------------MODEL LISTENERS-------------------------------------------*/

/* listener to the model that modifies the sound */
wave.addDependant({ |wave, what, val|

  /* sound updater only does something if either a file or a recording is loaded */
  if(wave.waveType.notNil,{
    var i = val.at(0); // first element is the selection index

    if (synths.notNil,{
      synths[i].set(
        'freq',if(wave.selectionSize(i) == 0, {0}, {1/wave.selectionDuration(i)}),
        'pos',wave.selectionStart(i)/wave.sampleRate,
        'dur', wave.selectionDuration(i)*wave.durFactor(i),
        'randStart',wave.randomStart(i),
        'randPan',wave.randomPan(i),
        'pitch',if(wave.reversed(i),{wave.pitchFactor(i).neg},{wave.pitchFactor(i)}),
        'amp',wave.amplitude(i),
        /* path buggy behaviour : pan = 1 plays as it was pan = -1 */
        'pan',( (wave.pan(i)==1).if(0.99999,wave.pan(i))),
        'posLFO', wave.posLFO(i),
        'ampLFO', wave.ampLFO(i),
        'pitchLFO', wave.pitchLFO(i),
        'maxPitchLFO', wave.maxPitchLFO(i)
      );
    });
  });
});

/* listener to the model that modifies the GUI */
wave.addDependant({arg wave, what, val;
  if( (val[0] == wave.currentSelection) or: ( ( what == 'pan') or: ( what == 'amplitude') )){
    /* pan and amplitude are visible for all the selection, so they always get updated */
    switch(what,
      'currentSelection',{
        {
          var index = val[0];
          /* update the gui box */
          currentSelectionBox.value = index;
          /* set the duration slider to the value of the selected selection  */
          durSlider.value_(wave.durFactor(index));
          pitchSlider.value_(wave.pitchFactor(index));
          /* set the randomStart and randomPan box to the value of the selected slection */
          randomStartNumBox.value_(wave.randomStart(index));
          randomPanNumBox.value_(wave.randomPan(index));

          reverseCheck.value_(wave.reversed(index));

          posLFO.value_(wave.posLFO(index));
          ampLFO.value_(wave.ampLFO(index));
          pitchLFO.value_(wave.pitchLFO(index));
          pitchLFOSlider.value_(wave.maxPitchLFO(index));

          //wave.view.scrollTo(wave.selectionStart(index)/wave.view.numFrames);
        }.defer;
      },
      'durFactor',{
        /* val is an array [index, val] */
        {durSlider.value_(val[1]);}.defer;
      },
      'randomStart',{
        {randomStartNumBox.value_(val[1]);}.defer;
      },
      'randomPan',{
        {randomPanNumBox.value_(val[1]);}.defer;
      },'pitchFactor',{
        {pitchSlider.value_(val[1]);}.defer;
      },
      'amplitude',{
        {volumeFaders.at(val[0]).value_(val[1].ampdb);}.defer;
      },
      'pan',{
        {panKnobs.at(val[0]).value_(wave.controlSpecOf('pan').unmap(val[1]));}.defer;
      },
      'reverse',{
        {reverseCheck.value_(val[1])}.defer;
      },
      'posLFO',{
        {posLFO.value_(val[1])}.defer;
      },
      'ampLFO', {
        {ampLFO.value_(val[1])}.defer;
      },
      'pitchLFO',{
        {pitchLFO.value_(val[1])}.defer;
      },
      'maxPitchLFO',{
        {pitchLFOSlider.value_(val[1])}.defer;
      }
    );
  };
});

/* ----------------------ADDITIONAL MIDI ACTIONS ------------------------------*/
additionalActions = (
  play: {| val, ccnum, chan, src|
    /* val is > 0 upon press and 0 upon release so change the state only upon press */
    if(val > 0){
      /* press the button */
      { playBtn.valueAction_((playBtn.value == 0).if(1,0)) }.defer;
    };
  },

  record: {| val, ccnum, chan, src|
    [ val, ccnum, chan ].postln;
    /* val is > 0 upon press and 0 upon release so change the state only upon press */
    if(val > 0){
      /* press the record button */
      { ("record "++recordBtn.value).postln ;recordBtn.valueAction_((recordBtn.value == 0).if(1,0));}.defer;
    };
  };

);

/* ------------------------------BUTTON ACTIONS--------------------------------*/
playBtn.action = {arg button;
  /* do nothing if no file is loaded */
  if(wave.waveType.isNil){
    "no file loaded".postln;
    ^false;
  };

  if(button.value == 1,{
    /* button is in play state */
    synths =  Array.fill(wave.numSelections,{ arg i;
      if(midiEnableBtns[i].value == 1){
        {}; // avoids exception when .free and .set are called
      }{
        var dedicatedOut = additionalControls.dedicatedOuts[i] != c_DISABLED_OUT;
        Synth.new(dedicatedOut.if('granularLooperMono', 'granularLooper'),[
          'buffer',buffer,
          'freq',if(wave.selectionSize(i) == 0, 0, 1/wave.selectionDuration(i)),
          'dur',wave.selectionDuration(i)*wave.durFactor(i),
          'pos',wave.selectionStart(i)/wave.sampleRate,
          'randStart',wave.randomStart(i),
          'randPan',wave.randomPan(i),
          'pitch',if(wave.reversed(i),{wave.pitchFactor(i).neg},{wave.pitchFactor(i)}),
          'amp',wave.amplitude(i),
          'pan',wave.pan(wave.controlSpecOf('pan').unmap(i)),
          'posLFO', wave.posLFO(i),
          'ampLFO', wave.ampLFO(i),
          'pitchLFO', wave.pitchLFO(i),
          'maxPitchLFO', wave.maxPitchLFO(i),
          'out', dedicatedOut.if({additionalControls.dedicatedOuts[i]},{additionalControls.out})
        ]);
      };
    });
    },{
      /* button is in stop state */
      synths.do(_.free);
      synths = nil;
  });
};


openBtn.action = {
  Dialog.openPanel({ arg path;
    var file;
    protect {
      file = SoundFile.new;
      file.openRead(path);

      if(file.numChannels != 1){
        PGranularMessageDialog("Sample must be mono");
      }{
        /* reads the audio file in the server */
        if(buffer.notNil,{buffer.free});
        buffer = Buffer.read(s,path);

        /* reads the audio file in the sound wave */
        wave.loadFile(file);
      }
    }{
      file.close;
    }
  });
};

recordBtn.action = {arg button;
  if(button.value == 1,{
    /* clean before use */
    if(buffer.notNil,{buffer.free});
    buffer = Buffer.alloc(s, 44100 * additionalControls.bufferLen, 1); // a four second 1 channel Buffer
    recorder = Synth.new('micRecorder',['buffer',buffer,'in',additionalControls.in]);
    },{
      /* stop recording */
      recorder.set('run',0);
      buffer.loadToFloatArray(action: {arg floatArray;
        {
          wave.loadData(floatArray);
        }.defer;
      });
  });
};

midiPanel = PGranularMidiPanel.new(wave, additionalActions, {buffer}, additionalControls);
midiBtn.action = {arg thisButton;
  thisButton.enabled = false;


  midiPanel.onClose = {
    /* re-enable the midi button */
    "onClose".postln;
    thisButton.enabled = true;
  };

  /* makes the panel visible */
  midiPanel.showGUI;
};

scopeBtn.action = {arg button;
  s.scope(2);
};

saveConfigBtn.action = {arg button;
  /* to save a config the path of the wav file must be saved as well */
  if(wave.wavePath.isNil){
    PGranularMessageDialog("Save Record to disk first");
  }{
    Dialog.savePanel({ arg path;
      try {
        wave.saveConfig(path,additionalControls);
        PGranularMessageDialog("Configuration saved to " ++ path);
      }{|e|	PGranularMessageDialog(e.what)};
    });
  }
};

loadConfigBtn.action = {arg button;
  Dialog.openPanel({ arg path;
    try {
      var config = wave.loadConfig(path);
      var soundFilePath = config[1];
      var additionalConfig = config[2];

      /* copy the config into the additionalControls event */
      additionalControls.clear;
      additionalConfig.keysValuesDo {|k,v|
        additionalControls[k] = v;
      };

      /* update the views according to the new additional controls */
      attackNumBox.value = additionalControls.attack;
      decayNumBox.value = additionalControls.decay;
      selectionStepNumBox.value = additionalControls.bufferLen;
      bufferLenNumBox.value = additionalControls.selectionStep;
      inNumBox.value = additionalControls.in;
      outNumBox.value = additionalControls.out;
      additionalControls.dedicatedOuts.do {|item, i|
        var bgcolor = (item == c_DISABLED_OUT).if(c_SUBVIEW_COLOR, Color.white);

        if(item != c_DISABLED_OUT){
          dedicatedOutNumBoxes[i].setColors(numBackground:bgcolor).enabled_(item != c_DISABLED_OUT)
          .value_((item != c_DISABLED_OUT).if(item,0));

        };

        dedicatedOutEnableBtns[i].value_(item != c_DISABLED_OUT);

      };
      additionalControls.midiEnabledSelections.do {|item, i| midiEnableBtns[i].value_(item == true)};


      /* reads the audio file in the server */
      if(buffer.notNil,{buffer.free});
      buffer = Buffer.read(s,soundFilePath);
    }{|e|	PGranularMessageDialog(e.what); e.postProtectedBacktrace};
  });
};

saveRecordBtn.action = {arg button;
  if(wave.wavePath.notNil or: wave.waveType.isNil){
    PGranularMessageDialog.new("Nothing to save");
  }{
    Dialog.savePanel({ arg path;
      try{
        wave.saveData(path);
        PGranularMessageDialog("Record saved to "++path);
      }{|e| PGranularMessageDialog(e.what)};
    });
  }
};

/*--------------------------------FOCUS ACTIONS--------------------------------*/
volumeFaders.do{arg item, index;
  item.sliderView.focusGainedAction = {wave.currentSelection_(index)};
  item.numberView.focusGainedAction = {wave.currentSelection_(index)};
};

panKnobs.do{arg item, index;
  item.focusGainedAction = {wave.currentSelection_(index)};
};

/*-----------------------------KEY ACTIONS--------------------------------------*/

/* global action to change selection using ctrl+n, with n = 0..923 */
View.globalKeyDownAction = {
  arg view,char,modifiers,unicode,keycode;

  /* numbers keys change the current selection index */
  if(modifiers.isCtrl,{
    [$0,$1,$2,$3,$4,$5,$6,$7,$8,$9].do({arg item,i;
      if(char == item,{
        ~pgranularwaves.do({arg wave,j;
          wave.currentSelection_(i);
        });
      });
    });

    /* use the ` key, which is next to 1, as 0 to have selection sorted */
    if(charCodes['GraveAccent'] == keycode){
      ~pgranularwaves.do({arg wave,j;
        wave.currentSelection_(0);
      });
    };

    if(charCodes[$r] == keycode){
      recordBtn.valueAction = ((recordBtn.value + 1) % 2);
    };

    if(charCodes['Tab'] == keycode){ // ctrl esc
      wave.view.focus;
    }
  });
};

/* actions to reset the parameters upon pressing Esc */

makeResetActionFunc = {arg param, selectionIndex = nil;
  /* selectionIndex is a placeholder to know whether the selection
  needs to be fetched from the wave (selectionIndex == nil) or it's fixed
  localSelectionIndex is the actual vaue used
  */
  var localSelectionIndex = selectionIndex;
  {arg view,char,modifiers,unicode,keycode;
    if(selectionIndex.isNil,{
      localSelectionIndex = wave.currentSelection;
    });
    if(keycode == charCodes['Esc'],{wave.reset(localSelectionIndex,param);});
  };
};

durSlider.sliderView.keyUpAction_(makeResetActionFunc.value('durFactor'));
durSlider.numberView.keyUpAction_(durSlider.sliderView.keyUpAction);

pitchSlider.sliderView.keyUpAction_(makeResetActionFunc.value('pitchFactor'));
pitchSlider.numberView.keyUpAction_(pitchSlider.sliderView.keyUpAction);

randomStartNumBox.numberView.keyUpAction_(makeResetActionFunc.value('randomStart'));
randomPanNumBox.numberView.keyUpAction_(makeResetActionFunc.value('randomPan'));

posLFO.numberView.keyUpAction_(makeResetActionFunc.value('posLFO'));
posLFO.view.keyUpAction_(makeResetActionFunc.value('posLFO'));

ampLFO.numberView.keyUpAction_(makeResetActionFunc.value('ampLFO'));
ampLFO.view.keyUpAction_(makeResetActionFunc.value('ampLFO'));

pitchLFO.numberView.keyUpAction_(makeResetActionFunc.value('pitchLFO'));
pitchLFO.view.keyUpAction_(makeResetActionFunc.value('pitchLFO'));

pitchLFOSlider.sliderView.keyUpAction_(makeResetActionFunc.value('maxPitchLFO'));
pitchLFOSlider.numberView.keyUpAction_(makeResetActionFunc.value('maxPitchLFO'));

panKnobs.do({arg item, i;
  item.keyUpAction_(makeResetActionFunc.value('pan',i));
});

volumeFaders.do({arg item, i;
  item.sliderView.keyUpAction_(makeResetActionFunc.value('amplitude',i));
  item.numberView.keyUpAction_(item.sliderView.keyUpAction);
});

/* Wave control keys */
wave.view.keyDownAction = { arg view,char,modifiers,unicode,keycode;

  //(char ++ " " ++ modifiers ++ " "++ keycode).postln;

  /* Esc will set the current selection lenght to 0 */
  switch(keycode,
    charCodes[$o],{
      if(modifiers.isCtrl){
        openBtn.valueAction_();
      };
    },
    charCodes['Esc'],{
      wave.reset(wave.currentSelection,'selectionSize');
    },
    charCodes['SpaceBar'],{
      var toggle = ( playBtn.value + 1 ) % 2; //toggle
      playBtn.valueAction_(toggle);
    },
    charCodes['Up'],{
      var increment = (modifiers.isCtrl).if(additionalControls.selectionStep,1) *
      wave.samplesPerPixel;
      var size = wave.selectionSize(wave.currentSelection);
      size = wave.withinNumFrames(size);
      wave.setSelection(wave.currentSelection,
        [wave.view.timeCursorPosition,size+increment];
      );
    },
    charCodes['Down'],{
      var increment = (modifiers.isCtrl).if(additionalControls.selectionStep,1) *
      wave.samplesPerPixel;
      var size = wave.selectionSize(wave.currentSelection);
      size = wave.withinNumFrames(size);
      wave.setSelection(wave.currentSelection,
        [wave.view.timeCursorPosition,size-increment];
      );
    },
    charCodes['Left'],{
      /* decrements pos by one pixel or 5 pixels is ctrl is held.
      * pos is  expressed in frames so one pixel is wames.samplesPerPixel */
      var pos = wave.view.selectionStart(wave.currentSelection);
      pos = pos - ((modifiers.isCtrl).if(additionalControls.selectionStep,1) *
        wave.samplesPerPixel);
      pos = wave.withinNumFrames(pos);
      wave.setSelectionStart(wave.currentSelection,pos);
    },
    charCodes['Right'],{
      /* increments pos by one pixel or 5 pixels is ctrl is held.
      * pos is  expressed in frames so one pixel is wames.samplesPerPixel */
      var pos = wave.view.timeCursorPosition;
      pos = pos + ((modifiers.isCtrl).if(additionalControls.selectionStep,1) *
        wave.samplesPerPixel);
      pos = wave.withinNumFrames(pos);
      wave.setSelectionStart(wave.currentSelection,pos);
    },
    charCodes['PageUp'],{
      wave.view.scroll(-0.5);
    },
    charCodes['PageDown'],{
      wave.view.scroll(0.5);
    },
    charCodes['F1'],{
      PGranularConfirmDialog.new("reset all parameters ? ", {wave.resetAll;});
    },
    charCodes['Equal'],{
      wave.zoom(1/zoomFactor);
    },
    charCodes['Minus'],{
      wave.zoom(zoomFactor);
    },
    charCodes['Ins'],{
      wave.setSelectionStart(wave.currentSelection, wave.view.scrollPos * view.numFrames);
    }
  );
};

/* init PGranular with silence so selections and other things are possible */
wave.loadData(0.0 ! (44100 * additionalControls.bufferLen)) ; // a four second 1 channel Buffer

/* sound wave configuration */
wave.view.elasticMode = true;
wave.view.timeCursorOn = true;
wave.view.timeCursorColor = Color.white;
wave.view.timeCursorPosition = 0;
wave.view.drawsWaveForm = true;
wave.view.gridOn = true;
wave.view.gridResolution = 1;
/* sets the selection to the handier value 1 .*/
wave.currentSelection_(0);
/* window configuration */

window.onClose = {
  /* removes itself from wave list */
  ~pgranularwaves.remove(wave);
  /* button is in stop state */
  synths.do({arg item,i;
    item.free;
  });
  synths = nil;
  if(midiPanel.notNil,{midiPanel.dispose});
};

s.waitForBoot({
  window.front;
  /* looks like gridOn = false only works after the window is brought to the front */
  wave.view.gridColor = Color.black;
  wave.view.gridOn = false;
});



)


