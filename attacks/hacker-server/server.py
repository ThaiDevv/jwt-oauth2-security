from flask import Flask

app = Flask(__name__)

@app.route('/')
def home():
    return "💀 Hacker Server Placeholder - Ready for implementation"

if __name__ == '__main__':
    # Keep port 4000 as configured in docker-compose
    app.run(host='0.0.0.0', port=4000)
