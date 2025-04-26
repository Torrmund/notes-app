from flask import Flask, render_template, request, redirect
from flask_sqlalchemy import SQLAlchemy
from sqlalchemy import text
import os
import logging

app = Flask(__name__)
app.config['SQLALCHEMY_DATABASE_URI'] = os.environ.get('DATABASE_URL', 'postgresql://postgres:postgres@localhost:5432/notes')
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False

# Логирование
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

db = SQLAlchemy(app)

class Note(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    title = db.Column(db.String(100), nullable=False)
    content = db.Column(db.Text, nullable=False)

@app.route('/')
def index():
    notes = Note.query.all()
    return render_template('index.html', notes=notes)

@app.route('/add', methods=['POST'])
def add():
    title = request.form['title']
    content = request.form['content']
    note = Note(title=title, content=content)
    db.session.add(note)
    db.session.commit()
    logger.info(f"Добавлена заметка: {title}")
    return redirect('/')

@app.route('/delete/<int:note_id>')
def delete(note_id):
    note = Note.query.get_or_404(note_id)
    db.session.delete(note)
    db.session.commit()
    logger.info(f"Удалена заметка ID: {note_id}")
    return redirect('/')

@app.route('/healthz')
def healthz():
    return 'OK', 200

@app.route('/readiness')
def readiness():
    try:
        db.session.execute(text("SELECT 1"))
        return 'Ready', 200
    except Exception as e:
        logger.error(f"Readiness check failed: {e}")
        return 'Not Ready', 500

def create_app():
    with app.app_context():
        db.create_all()
    return app
