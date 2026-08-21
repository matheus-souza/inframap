package com.inframap.frontend.ui.tour

enum class ProductTourStep(
    val stepNumber: Int,
    val title: String,
    val description: String,
    val highlightTarget: String,
) {
    WELCOME_NAV(
        stepNumber = 1,
        title = "Bem-vindo ao InfraMap!",
        description =
            "A barra lateral de navegação (NavRail) oferece acesso rápido ao Dashboard, " +
                "Dispositivos, Staging, Sub-redes, Fontes de Descoberta e Topologia da sua infraestrutura.",
        highlightTarget = "Navegação Principal (NavRail)",
    ),
    COMMAND_PALETTE(
        stepNumber = 2,
        title = "Paleta de Comandos (Ctrl+K)",
        description =
            "Use o atalho Ctrl+K (ou Cmd+K no Mac) ou clique na barra de busca para pesquisar instantaneamente " +
                "por recursos, navegar entre visualizações e executar ações.",
        highlightTarget = "Paleta de Comandos (Ctrl+K)",
    ),
    DISCOVERY_SOURCES(
        stepNumber = 3,
        title = "Fontes de Descoberta & Auto-Scan",
        description =
            "Configure fontes de varredura automatizadas por faixa CIDR ou agendamento " +
                "para identificar novos dispositivos ativos na sua rede em tempo real.",
        highlightTarget = "Fontes de Descoberta",
    ),
    STAGING_QUEUE(
        stepNumber = 4,
        title = "Fila de Staging & Inventário",
        description =
            "Dispositivos descobertos aguardam aprovação na fila de Staging. Valide e promova-os " +
                "para o inventário oficial de ativos com histórico completo.",
        highlightTarget = "Fila de Staging & Inventário",
    ),
    TOPOLOGY_CANVAS(
        stepNumber = 5,
        title = "Mapa de Topologia Interativo",
        description =
            "Explore a arquitetura de rede visualmente em um canvas interativo com suporte a pan, " +
                "zoom, layout automático e inspeção detalhada de nós.",
        highlightTarget = "Canvas de Topologia",
    ),
    ;

    companion object {
        val TOTAL_STEPS: Int get() = entries.size

        fun fromStepNumber(number: Int): ProductTourStep = entries.find { it.stepNumber == number } ?: WELCOME_NAV
    }
}
