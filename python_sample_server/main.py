from flask import Flask, jsonify, send_file, abort

app = Flask(__name__)


@app.route('/version', methods=['GET'])
def get_version_info():
    # יצירת נתונים ב-JSON
    response_data = {
        "version_information": "גרסה 1.0.1 זמינה להורדה \n להורדה.",
        "button_texts": ["הורד עכשיו", "הורד גרסה מתקדמת", " הורד אולטרה גרסה"],
        "url_download": [
            "http://192.168.29.141:8000//download/main.zip",
            "http://192.168.29.141:8000//download/main.zip",
            "http://192.168.29.141:8000//download/main.zip"
        ]
    }

    # החזרת הנתונים כ-JSON
    return jsonify(response_data)

@app.route('/download/<filename>')
def download_file(filename):
    try:
        # נניח שהקבצים נמצאים בתיקייה בשם 'files'
        file_path = f'files/{filename}'  # התיקייה שבה נמצאים הקבצים
        return send_file(file_path, as_attachment=True)
    except FileNotFoundError:
        abort(404)  # אם הקובץ לא נמצא, מחזירים שגיאת 404


if __name__ == '__main__':
    app.run(debug=True, host='0.0.0.0', port=8000)
