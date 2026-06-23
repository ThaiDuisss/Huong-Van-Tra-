interface SystemPlaceholderPageProps {
    title: string;
    description: string;
}

export function SystemPlaceholderPage({
    title,
    description
}: SystemPlaceholderPageProps) {
    return (
        <main className="grid min-h-screen place-items-center bg-pos-background px-6 text-gray-500">
            <section className="grid max-w-xl gap-3 rounded-3xl bg-pos-surface p-8 shadow-pos-panel">
                <p className="text-sm font-semibold uppercase tracking-[0.14em] text-hvt-green-800">
                    Manufacturing
                </p>
                <h1 className="text-3xl font-bold">{title}</h1>
                <p className="text-gray-500">{description}</p>
            </section>
        </main>
    );
}
