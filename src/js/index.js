import React from 'react';
import ReactDOM from 'react-dom';
import sodium from 'libsodium-wrappers';
import initSqlJs from 'sql.js';
import pako from 'pako';
import AceEditor from 'react-ace';
import "ace-builds/src-noconflict/mode-sql";
import "ace-builds/src-noconflict/theme-textmate";
import styles from 'bulma/css/bulma.css';
import { saveAs } from 'file-saver';

window.React = React;
window.ReactDOM = ReactDOM;
window.sodium = sodium;
window.SQL = null;
window.pako = pako;
window.AceEditor = AceEditor;
window.styles = styles;
window.saveAs = saveAs;

// The following overwrites window.SQL once it's loaded. All we have to do is wait for it to resolve
window.sqlReady = initSqlJs({ locateFile: filename => `/${filename}` });
