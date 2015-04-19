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


PGranularMessageDialog {

	*new {arg message;
		super.new.init(message);
	}

	init {arg message;
		var window = Window.new("Message", Rect(200,200,255,100),false).alwaysOnTop_(true);
		var text = StaticText.new;
		var button = Button();

		text.string = message;
		button.states_([[ "OK" ]]);
		button.maxWidth_(150);
		button.action = {
			window.close;
		};

		window.layout = VLayout.new(
			[text, align: \center],
			[button, align: \center],
		);

		window.front;
	}


}

PGranularConfirmDialog {

	*new {arg message, okAction;
		super.new.init(message, okAction);
	}

	init {arg message, okAction;
		var window = Window.new("Message", Rect(200,200,255,100),false).alwaysOnTop_(true);
		var text = StaticText.new;
		var okButton = Button();
		var cancelButton = Button();

		text.string = message;

		okButton.states_([[ "OK" ]]);
		okButton.maxWidth_(75);
		okButton.action = {
			okAction.value;
			window.close;
		};

		cancelButton.states_([[ "Cancel" ]]);
		cancelButton.maxWidth_(75);
		cancelButton.action = {
			window.close;
		};

		window.layout = VLayout.new(
			[text, align: \center],
			[HLayout.new(
				okButton,
				cancelButton
			), align: \centrer ]
		);

		window.front;
	}


}
