import React from 'react';
import ReactDOM from 'react-dom';
import sodium from 'libsodium-wrappers';
import pako from 'pako';
import AceEditor from 'react-ace';
import "ace-builds/src-noconflict/mode-sql";
import "ace-builds/src-noconflict/theme-textmate";
import styles from 'bulma/css/bulma.css';
import { saveAs } from 'file-saver';

window.React = React;
window.ReactDOM = ReactDOM;
window.sodium = sodium;
window.pako = pako;
window.AceEditor = AceEditor;
window.styles = styles;
window.saveAs = saveAs;
