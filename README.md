# Simulador de Filas em Tandem

Simulador de eventos discretos para redes de filas em tandem, desenvolvido em Java. Utiliza gerador de números pseudoaleatórios LCG e escalonador baseado em `PriorityQueue`. Suporta topologias genéricas com roteamento probabilístico entre filas.

---

## Como compilar

No terminal, dentro da pasta onde o arquivo está salvo:

```bash
javac SimuladorFilasTandem.java
```

---

## Como executar

```bash
java SimuladorFilasTandem
```

A saída exibirá, para cada fila da rede:
- Número de perdas de clientes
- Tempo acumulado por estado (número de clientes no sistema)
- Probabilidade de cada estado
- Tempo global da simulação e total de aleatórios consumidos

---

## Cenário de validação padrão

O simulador já vem configurado com o seguinte cenário:

| Fila | Tipo | Servidores | Capacidade | Chegadas | Atendimento |
|------|------|-----------|------------|----------|-------------|
| Fila 1 | G/G/2/3 | 2 | 3 | entre 1 e 4 | entre 3 e 4 |
| Fila 2 | G/G/1/5 | 1 | 5 | — (sem chegadas externas) | entre 2 e 3 |

- Roteamento: 100% dos clientes atendidos na Fila 1 passam para a Fila 2; clientes atendidos na Fila 2 saem do sistema.
- Primeiro cliente chega no tempo `1.5`.
- Simulação encerra ao consumir `100.000` aleatórios.

---

## Como adaptar para outra topologia

Abra o arquivo `SimuladorFilasTandem.java` e edite o método `main()`.

**1. Defina as filas:**

```java
Fila[] filas = new Fila[] {
    // new Fila(nome, servidores, capacidade, minAtend, maxAtend, minCheg, maxCheg)
    new Fila("Fila 1", 2, 3, 3.0, 4.0, 1.0, 4.0),
    new Fila("Fila 2", 1, 5, 2.0, 3.0, 0.0, 0.0), // 0,0 = sem chegadas externas
};
```

**2. Defina o roteamento:**

```java
// routing[i][j] = probabilidade de ir da fila i para a fila j
// Se a soma da linha for menor que 1.0, a fração restante sai do sistema
double[][] routing = new double[][] {
    {0.0, 1.0},  // Fila 1: 100% vai para Fila 2
    {0.0, 0.0},  // Fila 2: 100% sai do sistema
};
```

Exemplo com roteamento probabilístico (3 filas):

```java
double[][] routing = new double[][] {
    {0.0, 0.7, 0.3},  // Fila 1: 70% para Fila 2, 30% para Fila 3
    {0.0, 0.0, 0.0},  // Fila 2: 100% sai do sistema
    {0.0, 0.0, 0.0},  // Fila 3: 100% sai do sistema
};
```

**3. Defina o ponto de entrada e o tempo da primeira chegada:**

```java
int filaComChegadasExternas = 0; // índice da fila que recebe clientes externos
double tempoPrimeiraChegada = 1.5;
```
