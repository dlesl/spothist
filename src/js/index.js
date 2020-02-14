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
const sqlPromise = initSqlJs({ locateFile: filename => `/${filename}` });

const appElem = document.getElementById("app");

const loader = (promise, name) => {
    const el = document.createElement("p");
    el.innerText = `Loading ${name}...`;
    appElem.appendChild(el);
    return promise
        .then(() => el.innerText += "done")
        .catch(e => el.innerText += `failed: ${e}`);
};

// we await this in core.cljs before starting the app
window.jsInit =
    Promise.all([loader(sodium.ready, "libsodium"),
    loader(sqlPromise, "SQLite")]);
