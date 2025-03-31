set dotenv-path := "./local.env"

app-dev:
    #!/usr/bin/env bash
    set -euxo pipefail
    echo "Running local dev server"
    export ENV=Local
    (
        trap 'kill 0' SIGINT; 
        scala-cli run . --restart --main-class docrecall.DocRecall & 
        npm run tailwind:watch --workspace app &
        npm run esbuild:watch --workspace app
    )

app-compile:
    scala-cli compile .

app-infra:
    docker-compose -f ./docker-compose.yml --env-file local.env up

app-clean:
    scala-cli clean .

[working-directory: 'eval']
eval:
    uv run deepeval set-local-model --model-name="deepseek-r1:1.5" \
        --base-url="http://localhost:4000" \
        --api-key="litellm"
    uv run deepeval test run ./test_eval.py

test:
    #!/usr/bin/env bash
    set -euxo pipefail
    echo "Running tests"
    export ENV=Test
    scala-cli test .

test-only filter:
    #!/usr/bin/env bash
    set -euxo pipefail
    echo "Running tests for {{filter}}"
    export ENV=Test
    scala-cli test . --test-only "{{filter}}"

ollama-serve:
    echo "Using $OLLAMA_CONTEXT_LENGTH ctx length"
    ollama serve
