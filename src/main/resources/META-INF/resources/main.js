document.addEventListener("DOMContentLoaded", function(event) {
	setup();
});

var resultsDiv;
var queryInput;
var timeout;

function setup() {
	resultsDiv = document.getElementById('results');
	queryInput = document.getElementById('q');

	queryInput.addEventListener('input', function(event) {
		if (timeout) 
			window.clearTimeout(timeout);
		timeout = window.setTimeout(reloadResults, 500);
	});

	timeout = window.setTimeout(reloadResults, 1);
}

async function reloadResults() {
	const value = queryInput.value.trim();
	const response = await fetch('/document/find?' + new URLSearchParams({q: value}), {
		headers: {
			'Accept': 'application/json'
		}
	});
	const results = await response.json();
	console.log(results);

	if (!results || results.length == 0) {
		resultsDiv.innerText = "Leider wurden keine Ergebnisse gefunden.";
	} else {
		function attr(attr, inf) {
			if (attr) {
				return ` <small><b>${inf}</b> ${attr}</small> `
			} else {
				return ''
			}
		}

		resultsDiv.innerHTML = results.map(
			r => `<div>
                     <h3><a href="/document/${r.document.id}/">${r.document.title}</a></h3>
							${attr(r.document.author, "von")}
							${attr(r.document.date, "Datum:")}
							${attr(r.document.lang, "Sprache:")}
							<br/>
							${r.snippedHtml
			? `<p>${r.snippedHtml}</p>`
			: ""}
            			</div>`).join(' ');
	}
}

async function upload() {
	let files = document.getElementById('file').files;

	for (var i = 0; i < files.length; i++) {
		let file = files.item(i);
		status("Hochladen " + i + " / " + files.length);
		try {
			let res = await fetch('/document/', {
				method: 'POST',
				body: file
			});
		} catch (e) {
			status("Fehler beim Upload :'( " + e);
			console.log(e);
			return;
		}
	}
	await reloadResults();
}

function status(status) {
	resultsDiv.innerText = status;
}
